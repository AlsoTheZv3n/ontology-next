# NEXO Ontology Engine

A multi-tenant ontology platform with typed objects, semantic search, LLM
agents, real-time CDC, and a full audit / compliance layer. Built on
open-source technologies.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  React 19 + Vite + TypeScript                                   │
│  Object Explorer · Graph View · Ontology Builder · API Keys     │
├─────────────────────────────────────────────────────────────────┤
│  Spring for GraphQL · REST · WebSocket                          │
├──────┬──────┬───────┬──────┬──────┬─────┬──────┬─────┬─────────┤
│ Type │ Act- │ Con-  │ ML   │ LLM  │ CDC │ Noti- │ Rate │ GDPR / │
│ Reg. │ ion  │ nector│ Emb- │ Agent│ DLQ │ fic.  │ Lim. │ Audit  │
│      │ Eng. │ OAuth2│ edds │ Tools│     │ S/T/I │      │ Erasure│
├──────┴──────┴───────┴──────┴──────┴─────┴──────┴─────┴─────────┤
│  Entity-Resolution · Lineage · Workflows · Dedup-Merge          │
├─────────────────────────────────────────────────────────────────┤
│  PostgreSQL 16 + pgvector · Redis · Flyway V1–V31               │
│  Row-Level-Security · AES-256-GCM · Testcontainers              │
└─────────────────────────────────────────────────────────────────┘
```

## Feature Status

All domains below are real code backed by unit tests (164 passing), not
placeholders. See [todo/](todo/) for the gap analysis (files gitignored).

| Domain | Highlights |
| :--- | :--- |
| **Ontology** | Typed `object_types`, `property_types`, `link_types`. Validation at write time. Traversal + typed links |
| **Entity Resolution** | Jaro-Winkler + EXACT match, Soundex-based blocking for bulk scans |
| **Deduplication** | Pair detection + reversible merge (soft-delete + `merged_into` redirect + unmerge) |
| **Semantic Search** | Real transformer embeddings (DJL + all-MiniLM-L6-v2, 384-dim) via pgvector HNSW |
| **LLM Agent** | Tool-calling loop (Anthropic / OpenAI / Ollama / Fallback keyword router) |
| **Multi-Tenancy** | JWT + API-key prefix auth, `TenantAwareDataSource` sets `app.tenant_id` per connection, RLS enforced on every tenant table |
| **CDC** | Redis-Streams consumer with dead-letter queue, replay + discard admin endpoints, Debezium docker-compose overlay for the Kafka path |
| **Data Lineage** | Per-field `property_lineage` with source type (USER / CDC / CONNECTOR / ACTION / AGENT), retention job |
| **Workflows** | LOG / CONDITION / WAIT / NOTIFY / WEBHOOK / EXECUTE_ACTION step executors, long-wait resume-later scheduler |
| **Notifications** | Slack / Teams / InApp channels, fixed-delay dispatcher draining a PENDING queue, @mention + watcher fan-out |
| **Rate Limiting** | Distributed Redis INCR counter, 3 fail-modes (FAIL_OPEN / FAIL_CLOSED / LOCAL_FALLBACK) |
| **GDPR Art. 17** | Recursive JSONB walker with GIN-indexed scan, admin + self-service erasure endpoints, idempotent via sha256 |
| **OAuth2** | Authorize / callback flow, state-token replay protection (10 min TTL), AES-256-GCM encrypted secrets + key rotation via legacy-key fallback |
| **API Keys** | SHA-256-hashed storage, scope-based auth, management UI under `/settings/api-keys` |
| **Export** | RFC-4180 CSV exporter |

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| Backend | Spring Boot 3.4, Java 21, Spring GraphQL, Spring Security |
| Database | PostgreSQL 16 + pgvector, Flyway V1–V31 (31 migrations), pgvector HNSW |
| Frontend | React 19, TypeScript, Vite, Apollo Client, Tailwind v4, Cytoscape |
| ML | DJL + PyTorch engine, sentence-transformers/all-MiniLM-L6-v2 |
| Messaging | Redis Streams, WebSocket (STOMP) |
| Security | JWT (JJWT), BCrypt, AES-256-GCM, Row-Level Security, Gitleaks CI |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers (opt-in integration test) |
| CI | GitHub Actions — backend + frontend + TypeScript SDK + Python SDK |

## SDKs

Both SDKs ship with offline unit tests (8 each, all green) and live against a
running backend via the same `/graphql` + `/api/v1/*` endpoints.

```bash
# TypeScript
npm install @nexoai/ontology-sdk

# Python
pip install nexo-ontology
```

Source + tests in [sdks/](sdks/).

## Quick Start

### Prerequisites

- Java 21 and Maven 3.9+
- Node.js 20+ and npm
- Docker Desktop

### 1. Environment

```bash
cp .env.example .env
# REQUIRED — generate an encryption key
openssl rand -base64 32  # paste into NEXO_ENCRYPTION_KEY
```

### 2. Infrastructure

```bash
docker compose up -d postgres redis
```

### 3. Backend

```bash
cd backend
mvn spring-boot:run
```

Ready when you see `Started OntologyEngineApplication`.
- REST base:  `http://localhost:8081/api/v1`
- GraphiQL:   `http://localhost:8081/graphiql`
- Health:     `http://localhost:8081/actuator/health`
- Prometheus: `http://localhost:8081/actuator/prometheus`

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

Default Vite port (browser opens automatically).

### 5. Optional

```bash
# n8n (workflow automation)
docker compose up -d n8n   # http://localhost:5678

# Full Debezium CDC stack (source Postgres + Kafka + Connect)
docker compose -f docker-compose.yml -f docker-compose-cdc.yml up -d
./docker/cdc/register-connector.sh
```

## API Examples

### Auth

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@demo.nexo.ai","password":"admin123"}' | jq -r .token)
```

### GraphQL — semantic search

```graphql
{
  semanticSearch(query: "high revenue enterprise clients", limit: 5) {
    id score properties
  }
}
```

### GraphQL — LLM agent chat

```graphql
mutation {
  agentChat(input: { message: "how many customers do we have?" }) {
    message
    toolCalls { tool }
  }
}
```

### REST — create API key

```bash
curl -X POST http://localhost:8081/api/v1/api-keys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"ci-key","scopes":["read","write"]}'
# Response: { "id":"…", "rawKey":"nxo_…", "name":"ci-key" }
# The rawKey is shown ONCE — store it immediately.
```

### REST — GDPR self-service erase

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/api/v1/me/erase/dry-run | jq .
# preview affected objects without mutating

curl -X POST http://localhost:8081/api/v1/me/erase \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"confirm":"DELETE MY DATA"}'
```

## Verifying Your Install

```bash
# 1. Unit tests
cd backend && mvn test
# Expect: Tests run: 164, Failures: 0, Errors: 0, Skipped: 2

# 2. Spring-context integration test (needs Docker)
cd backend && mvn test -Dnexo.test.integration=true -Dtest=ApplicationContextIT

# 3. SDK tests
cd sdks/typescript && npm test        # 8 passed
cd sdks/python && python -m pytest    # 8 passed

# 4. Verify every migration applied against the running DB
docker exec -it $(docker ps -qf name=postgres) \
  psql -U nexo -d nexo_ontology \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"
```

## Project Structure

```
ontology-next/
├── backend/                                Spring Boot + JPA + GraphQL
│   ├── src/main/java/com/nexoai/ontology/
│   │   ├── adapters/in/rest/               REST controllers
│   │   ├── adapters/in/graphql/            GraphQL resolvers
│   │   ├── adapters/out/persistence/       JPA entities + repositories
│   │   └── core/                           Business logic (24 packages)
│   │       ├── agent/                      LLM + tool calling
│   │       ├── apikey/                     API key issuance
│   │       ├── cdc/                        Change-data-capture consumer
│   │       ├── collab/                     Comments, mentions, watchers
│   │       ├── connector/                  OAuth2 + sync framework
│   │       ├── crypto/                     AES-256-GCM + key rotation
│   │       ├── entityresolution/           Jaro-Winkler match engine
│   │       ├── gdpr/                       Art. 17 erasure
│   │       ├── lineage/                    Per-field provenance
│   │       ├── ml/                         DJL transformer embeddings
│   │       ├── notification/               Slack/Teams/InApp + dispatcher
│   │       ├── quality/                    Dedup scan + merge
│   │       ├── ratelimit/                  Redis INCR + fail-modes
│   │       ├── tenant/                     JWT + API key auth, RLS
│   │       └── workflow/                   Step executors + resume
│   └── src/main/resources/db/migration/    Flyway V1–V31
├── frontend/                               React 19 + Vite
│   └── src/pages/                          10 pages incl. ApiKeys
├── sdks/
│   ├── typescript/                         @nexoai/ontology-sdk
│   └── python/                             nexo-ontology
├── docker/                                 Docker configs incl. CDC overlay
├── .github/workflows/                      CI + secret scan
└── docker-compose.yml                      Postgres + Redis + n8n
```

## Known Gaps

Documented in [todo/](todo/) (folder is gitignored). Summary:

- **Heavy follow-ups:** SMTP email channel, HubSpot live OAuth2 sync, 5 missing
  frontend admin pages (workflow builder, quality dashboard, HITL approval,
  billing, GDPR admin).
- **Infrastructure:** Spring-context integration test works but requires Docker;
  not yet required in CI.
- **Test coverage:** 164 tests pass, but older V1–V23-era services remain
  lightly tested relative to the post-V24 additions.

## License

MIT
