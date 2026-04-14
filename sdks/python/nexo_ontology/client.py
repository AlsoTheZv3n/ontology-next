"""NEXO Ontology Fabric async client.

Uses httpx under the hood so the same client works in scripts, notebooks,
and long-running async services. For sync callers, wrap calls in
`asyncio.run(...)` or use `httpx.Client` directly through `raw_client`.

Authentication: either pass an existing bearer token / API key via the
``token`` argument, or call :meth:`NexoClient.login` with email + password
and the client stores the returned JWT.
"""
from __future__ import annotations

from typing import Any, Mapping, Optional

import httpx


class NexoApiError(RuntimeError):
    """Raised when the Fabric API returns a non-2xx response or GraphQL errors."""

    def __init__(self, status: int, message: str, body: Any = None):
        super().__init__(message)
        self.status = status
        self.body = body


class NexoClient:
    def __init__(
        self,
        base_url: str,
        token: Optional[str] = None,
        *,
        http_client: Optional[httpx.AsyncClient] = None,
        timeout: float = 30.0,
    ) -> None:
        if not base_url:
            raise ValueError("base_url is required")
        self._base_url = base_url.rstrip("/")
        self._token = token
        headers = {"content-type": "application/json"}
        if token:
            headers["authorization"] = f"Bearer {token}"
        self._client = http_client or httpx.AsyncClient(
            base_url=self._base_url, headers=headers, timeout=timeout
        )
        # If the caller provided their own client, respect their base_url and
        # just overlay auth.
        if http_client is not None and token:
            http_client.headers["authorization"] = f"Bearer {token}"

    @property
    def raw_client(self) -> httpx.AsyncClient:
        """Escape hatch: the underlying httpx client for endpoints the SDK hasn't wrapped yet."""
        return self._client

    @classmethod
    async def login(
        cls,
        base_url: str,
        email: str,
        password: str,
        *,
        http_client: Optional[httpx.AsyncClient] = None,
    ) -> "NexoClient":
        client = http_client or httpx.AsyncClient(base_url=base_url.rstrip("/"), timeout=30.0)
        try:
            resp = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
                headers={"content-type": "application/json"},
            )
        except Exception:
            if http_client is None:
                await client.aclose()
            raise
        if resp.status_code < 200 or resp.status_code >= 300:
            if http_client is None:
                await client.aclose()
            raise NexoApiError(resp.status_code, f"login failed ({resp.status_code})")
        token = resp.json()["token"]
        return cls(base_url, token=token, http_client=http_client)

    async def aclose(self) -> None:
        await self._client.aclose()

    async def __aenter__(self) -> "NexoClient":
        return self

    async def __aexit__(self, *_exc: Any) -> None:
        await self.aclose()

    async def gql(self, query: str, variables: Optional[Mapping[str, Any]] = None) -> Any:
        resp = await self._client.post("/graphql", json={"query": query, "variables": dict(variables or {})})
        data = resp.json()
        if resp.status_code < 200 or resp.status_code >= 300 or data.get("errors"):
            msg = ", ".join(e.get("message", str(e)) for e in data.get("errors", [])) or f"HTTP {resp.status_code}"
            raise NexoApiError(resp.status_code, msg, data)
        return data["data"]

    async def rest(self, path: str, *, method: str = "GET", json: Any = None) -> Any:
        resp = await self._client.request(method, path, json=json)
        if resp.status_code < 200 or resp.status_code >= 300:
            raise NexoApiError(resp.status_code, f"REST {path} failed ({resp.status_code})", resp.text)
        return resp.json() if resp.content else None

    # ------------------------------------------------------------------
    # Convenience wrappers — thin typed entry points for common workflows.

    async def list_object_types(self) -> list[dict[str, Any]]:
        return await self.rest("/api/v1/ontology/object-types")

    async def upsert_object(
        self, object_type: str, source_id: str, properties: Mapping[str, Any]
    ) -> dict[str, Any]:
        return await self.rest(
            "/api/v1/objects",
            method="POST",
            json={"objectTypeApiName": object_type, "sourceId": source_id, "properties": dict(properties)},
        )

    async def semantic_search(self, query: str, limit: int = 5) -> list[dict[str, Any]]:
        data = await self.gql(
            "query($q:String!,$l:Int!){ semanticSearch(query:$q,limit:$l){ id score properties } }",
            {"q": query, "l": limit},
        )
        return data["semanticSearch"]

    async def agent_chat(self, message: str) -> dict[str, Any]:
        data = await self.gql(
            "mutation($m:String!){ agentChat(input:{message:$m}){ message toolCalls{ tool } } }",
            {"m": message},
        )
        return data["agentChat"]
