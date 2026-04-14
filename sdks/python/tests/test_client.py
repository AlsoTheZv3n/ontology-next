"""Offline unit tests for NexoClient using respx to stub HTTP calls.

Requires `pip install -e ".[dev]"` to pick up respx and pytest-asyncio.
These tests do NOT need a running Fabric backend.
"""
from __future__ import annotations

import httpx
import pytest
import respx

from nexo_ontology import NexoApiError, NexoClient


@pytest.mark.asyncio
async def test_login_stores_token_and_sends_bearer_on_next_call():
    async with respx.mock(base_url="http://fabric") as router:
        router.post("/api/auth/login").respond(200, json={"token": "jwt-123"})
        router.get("/api/v1/ontology/object-types").respond(200, json=[])

        client = await NexoClient.login("http://fabric", "a@b.ai", "pw")
        await client.list_object_types()

        login_req = router.routes[0].calls.last.request
        assert login_req.headers["content-type"] == "application/json"
        list_req = router.routes[1].calls.last.request
        assert list_req.headers["authorization"] == "Bearer jwt-123"
        await client.aclose()


@pytest.mark.asyncio
async def test_login_raises_NexoApiError_on_401():
    async with respx.mock(base_url="http://fabric") as router:
        router.post("/api/auth/login").respond(401, json={"error": "bad creds"})
        with pytest.raises(NexoApiError) as exc:
            await NexoClient.login("http://fabric", "x", "y")
        assert exc.value.status == 401


@pytest.mark.asyncio
async def test_gql_unwraps_data_envelope():
    async with respx.mock(base_url="http://fabric") as router:
        router.post("/graphql").respond(
            200,
            json={"data": {"semanticSearch": [{"id": "o1", "score": 0.8, "properties": {"name": "Acme"}}]}},
        )
        async with NexoClient("http://fabric", token="t") as client:
            hits = await client.semantic_search("query", 3)
        assert len(hits) == 1
        assert hits[0]["id"] == "o1"


@pytest.mark.asyncio
async def test_gql_raises_when_errors_present():
    async with respx.mock(base_url="http://fabric") as router:
        router.post("/graphql").respond(200, json={"errors": [{"message": "forbidden"}]})
        async with NexoClient("http://fabric", token="t") as client:
            with pytest.raises(NexoApiError) as exc:
                await client.semantic_search("q")
        assert "forbidden" in str(exc.value)


@pytest.mark.asyncio
async def test_upsert_object_posts_normalized_payload():
    async with respx.mock(base_url="http://fabric") as router:
        route = router.post("/api/v1/objects").respond(200, json={"id": "o1"})
        async with NexoClient("http://fabric", token="t") as client:
            r = await client.upsert_object("customer", "ext-1", {"name": "X"})
        sent = route.calls.last.request.content.decode()
        assert '"objectTypeApiName":"customer"' in sent
        assert '"sourceId":"ext-1"' in sent
        assert r["id"] == "o1"


@pytest.mark.asyncio
async def test_rest_raises_NexoApiError_on_500():
    async with respx.mock(base_url="http://fabric") as router:
        router.get("/api/v1/boom").respond(500, text="internal")
        async with NexoClient("http://fabric", token="t") as client:
            with pytest.raises(NexoApiError) as exc:
                await client.rest("/api/v1/boom")
        assert exc.value.status == 500
        assert "internal" in exc.value.body


def test_missing_base_url_raises():
    with pytest.raises(ValueError):
        NexoClient("", token="t")


def test_trailing_slash_in_base_url_is_stripped():
    client = NexoClient("http://fabric/", token="t")
    assert client._base_url == "http://fabric"
