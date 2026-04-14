# NEXO Ontology SDKs

Client libraries for the Fabric REST + GraphQL API.

| Language   | Package                     | Location                |
| ---------- | --------------------------- | ----------------------- |
| TypeScript | `@nexoai/ontology-sdk`      | [`typescript/`](./typescript/) |
| Python     | `nexo-ontology`             | [`python/`](./python/)         |

Both clients expose the same surface: `login()`, `gql()`, `rest()` plus thin
wrappers for `upsertObject`, `semanticSearch`, and `agentChat`.

## Running the unit tests

These run fully offline — no backend needed.

```bash
# TypeScript
cd sdks/typescript
npm install
npm test

# Python
cd sdks/python
pip install -e ".[dev]"
pytest
```

## Live E2E (optional)

The spec in `todo/prod-10-sdk-e2e-testing.md` describes full end-to-end
tests against a running backend. Those are left as a manual-run step
because they need:

- the backend stack (`docker compose up -d`),
- seed users (`admin@demo.nexo.ai` / `admin123`) from the dev migration,
- a configured LLM provider (or `NEXO_LLM_PROVIDER=fallback` for deterministic
  keyword routing in CI).

Once the stack is healthy at `http://localhost:8081`, run:

```bash
NEXO_BASE_URL=http://localhost:8081 npm run test:e2e     # in sdks/typescript
NEXO_BASE_URL=http://localhost:8081 pytest tests/e2e     # in sdks/python
```

The Java SDK is intentionally omitted — server-side code already exposes
Java types directly, so consumers on the JVM typically pull in the
backend module rather than maintaining a parallel client.
