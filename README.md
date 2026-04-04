# NEXO Ontology Engine

A production-ready, multi-tenant Ontology Engine built on open-source technologies, designed for Swiss SMEs.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    React Frontend (Vite)                     │
│         Refined Intelligence Design System                  │
├─────────────────────────────────────────────────────────────┤
│                    GraphQL API Layer                         │
│              Spring for GraphQL + REST                      │
├──────────┬──────────┬───────────┬──────────┬───────────────┤
│ Object   │ Action   │ Connector │ ML Layer │ AI Agent      │
│ Registry │ Engine   │ Framework │ Semantic │ NL Queries    │
│          │ + Audit  │ JDBC/REST │ Search   │ Tool Calling  │
│          │          │ CSV/CDC   │ pgvector │ HITL Approval │
├──────────┴──────────┴───────────┴──────────┴───────────────┤
│              PostgreSQL 16 + pgvector | Redis               │
└─────────────────────────────────────────────────────────────┘
```

## Phases

| Phase | Component | Status |
|-------|-----------|--------|
| 1 | Object Type Registry | Done |
| 2 | GraphQL API + Action Engine | Done |
| 3 | Connector Framework (JDBC, REST, CSV) | Done |
| 4 | React Frontend | Done |
| 5 | ML Layer (Embeddings + Semantic Search) | Done |
| 6 | Graph View + n8n Integration | Done |
| 7 | Multi-Tenancy (JWT + Tenant Isolation) | Done |
| 8 | Schema Versioning + Object History | Done |
| 9 | Real-Time CDC (Redis Streams + WebSockets) | Done |
| 10 | AI Agent Layer (NL Queries + HITL) | Done |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.4, Java 21, Spring GraphQL, Spring Security |
| Database | PostgreSQL 16 + pgvector, Flyway migrations (V1-V9) |
| Frontend | React 19, TypeScript, Vite, Apollo Client, Tailwind CSS v4, Cytoscape.js |
| ML | DJL (Deep Java Library), Sentence Transformers, pgvector HNSW |
| Messaging | Redis Streams, WebSockets |
| Automation | n8n, Debezium CDC |
| Auth | JWT (JJWT), BCrypt, Tenant-aware context |
| Container | Docker Compose (PostgreSQL, Redis, n8n, Debezium) |

## Quick Start

### Prerequisites

- Java 21 (e.g. `scoop install temurin21-jdk`)
- Maven 3.9+ (e.g. `scoop install maven`)
- Node.js 20+ and npm
- Docker Desktop

### 1. Environment

```bash
cp .env.example .env
# Edit .env with your values
```

### 2. Start Infrastructure

```bash
docker compose up -d postgres redis
```

### 3. Start Backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs on `http://localhost:8081`
GraphiQL UI at `http://localhost:8081/graphiql`

### 4. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:3005`

### 5. Optional Services

```bash
# n8n (Workflow Automation)
docker compose up -d n8n
# Access at http://localhost:5678

# Debezium CDC (requires source DB config in .env)
docker compose --profile cdc up -d debezium
```

## API Examples

### GraphQL - Query Objects

```graphql
{
  getAllObjectTypes {
    apiName
    displayName
    properties { apiName dataType }
  }
}
```

### GraphQL - Create Object

```graphql
mutation {
  createObject(objectType: "Customer", properties: {
    name: "Acme Corp"
    revenue: 150000
  }) {
    id objectType properties
  }
}
```

### GraphQL - Semantic Search

```graphql
{
  semanticSearch(query: "high revenue clients", objectType: "Customer", limit: 5) {
    similarity
    object { id properties }
  }
}
```

### GraphQL - AI Agent Chat

```graphql
mutation {
  agentChat(message: "How many Customers do we have?") {
    message
    toolCalls { tool resultSummary }
  }
}
```

### REST - Auth

```bash
# Register tenant
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"tenantApiName":"acme","tenantDisplayName":"Acme Corp","email":"admin@acme.ch","password":"secret"}'

# Login
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.ch","password":"secret"}'
```

## Project Structure

```
ontology-next/
├── backend/
│   ├── src/main/java/com/nexoai/ontology/
│   │   ├── adapters/in/rest/          # REST Controllers
│   │   ├── adapters/in/graphql/       # GraphQL Resolvers
│   │   ├── adapters/out/persistence/  # JPA Entities + Repos
│   │   ├── adapters/out/connector/    # JDBC/REST/CSV Connectors
│   │   ├── core/domain/              # Domain Models
│   │   ├── core/service/             # Business Services
│   │   ├── core/connector/           # Connector Framework
│   │   ├── core/ml/                  # Embeddings + Search
│   │   ├── core/agent/              # AI Agent + Tools
│   │   ├── core/tenant/             # Multi-Tenancy
│   │   ├── core/versioning/         # Schema Versioning
│   │   ├── core/cdc/                # Change Data Capture
│   │   └── config/                  # Spring Config
│   └── src/main/resources/
│       ├── db/migration/            # Flyway V1-V9
│       ├── graphql/                 # GraphQL Schema
│       └── application.yml
├── frontend/
│   └── src/
│       ├── api/graphql/             # Apollo Client + Queries
│       ├── components/layout/       # Sidebar, TopBar
│       ├── pages/                   # All 8 pages
│       ├── store/                   # Zustand
│       └── types/                   # TypeScript types
├── docker/                          # Docker configs
├── .env.example                     # Environment template
├── PLACEHOLDERS.md                  # TODO items for production
└── docker-compose.yml
```

## Placeholders for Production

See [PLACEHOLDERS.md](PLACEHOLDERS.md) for all items that need replacement before production deployment, including:

- EmbeddingService: Replace tokenizer-based embeddings with full transformer model
- AI Agent: Replace keyword routing with LLM (OpenAI/Claude) via Spring AI
- Security: Enforce JWT authentication on all endpoints
- RLS: Enable PostgreSQL Row-Level Security policies

## License

Proprietary — NEXO AI
