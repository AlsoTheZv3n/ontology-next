# Placeholder & TODOs for Production Replacement

This document tracks all temporary/placeholder implementations that need to be replaced with production-quality solutions before going live.

---

## Phase 5 — ML Layer

### 1. EmbeddingService: Tokenizer-based Pseudo-Embeddings
- **File:** `backend/src/main/java/com/nexoai/ontology/core/ml/EmbeddingService.java`
- **What:** The `tokenizeToEmbedding()` method generates embeddings from token IDs only (no transformer inference). The `hashEmbedding()` method is a deterministic fallback.
- **Why:** Full DJL PyTorch model inference (`sentence-transformers/all-MiniLM-L6-v2`) requires downloading ~90MB model + PyTorch native libs (~500MB). The tokenizer-only approach works for PoC.
- **Replace with:** Load the full ONNX or PyTorch model via DJL `Criteria.builder()` and run actual sentence-transformer inference. Example:
  ```java
  Criteria<String, float[]> criteria = Criteria.builder()
      .setTypes(String.class, float[].class)
      .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2")
      .optTranslator(new SentenceTransformerTranslator())
      .build();
  ZooModel<String, float[]> model = ModelZoo.loadModel(criteria);
  Predictor<String, float[]> predictor = model.newPredictor();
  ```
- **Impact:** Similarity scores will jump from ~0.08 to semantically meaningful values (0.5-0.95). Semantic search becomes actually useful.
- **Effort:** Medium — need to write a `SentenceTransformerTranslator` or use ONNX Runtime instead of PyTorch.

### 2. PropertyExtractionService: Pattern-based NER
- **File:** `backend/src/main/java/com/nexoai/ontology/core/ml/PropertyExtractionService.java`
- **What:** Uses regex patterns for entity extraction (email, phone, money, URL, date, location). Location detection is hardcoded to a list of Swiss/German cities.
- **Replace with:** DJL model `dslim/bert-base-NER` for real Named Entity Recognition. Alternatively, call an external NLP API (e.g., Anthropic Claude, OpenAI).
- **Impact:** Will extract ORG, PERSON, LOC, MONEY entities from any language, not just pattern-matched values.
- **Effort:** High — requires BERT model loading, tokenization, and BIO-tag decoding.

### 3. SemanticSearch: Raw SQL String Concatenation
- **File:** `backend/src/main/java/com/nexoai/ontology/core/ml/SemanticSearchService.java`
- **What:** Uses string concatenation for the pgvector SQL query (not parameterized). This is NOT SQL-injectable since the embedding is a float array generated server-side, but it's not best practice.
- **Replace with:** Use a proper `PGobject` with pgvector type, or use Spring Data JPA `@Query(nativeQuery=true)` with a custom `AttributeConverter` for the vector type.
- **Effort:** Low — just need to implement a proper `VectorType` converter for Hibernate.

---

## Phase 6 — n8n Integration

### 4. n8n Webhook Secret: Hardcoded
- **File:** `backend/src/main/java/com/nexoai/ontology/adapters/in/rest/N8nInboundController.java`
- **What:** Webhook secret is loaded from `application.yml` property. For production, use a secrets manager.
- **Replace with:** Spring Vault integration or environment variable from Kubernetes secrets.
- **Effort:** Low.

### 5. n8n SideEffect: No Retry Logic
- **File:** `backend/src/main/java/com/nexoai/ontology/core/service/action/WebhookSideEffect.java` + `N8nSideEffect.java`
- **What:** Webhook calls fire-and-forget. No retry on failure, no dead-letter queue.
- **Replace with:** Spring Retry with exponential backoff, or move to a message queue (Redis/RabbitMQ) for reliable delivery.
- **Effort:** Medium.

### 6. N8n Inbound: No Idempotency
- **File:** `backend/src/main/java/com/nexoai/ontology/adapters/in/rest/N8nInboundController.java`
- **What:** Bulk import creates new objects every time (no upsert by external ID). The single-object endpoint also has no dedup.
- **Replace with:** Accept an `externalId` field in the payload, use `findByExternalIdAndDataSourceId` for upsert logic (like SyncJob does).
- **Effort:** Low.

### 7. N8n Workflow Templates: Example URLs
- **Files:** `docker/n8n/templates/*.json`
- **What:** Templates reference `http://nexo-backend:8081` (container name) and `https://api.example.com/data` (placeholder URL).
- **Replace with:** Use environment variables for base URLs. Update when deploying to production.
- **Effort:** Low.

---

## General

### Authentication: Permissive (Phase 7 implemented but not enforced)
- **File:** `backend/src/main/java/com/nexoai/ontology/config/SecurityConfig.java`
- **What:** JWT auth + TenantInterceptor implemented. BUT all endpoints are `permitAll()` — no endpoint requires authentication. The TenantInterceptor reads JWT if present, falls back to default tenant if not.
- **Replace with:** Change `permitAll()` to `authenticated()` on `/api/v1/**` and `/graphql` endpoints. Add JWT filter to Spring Security filter chain. Add proper role-based access control (OWNER/ADMIN/MEMBER/VIEWER).
- **Effort:** Medium — the JWT infrastructure is complete, just need to enforce it.

### RLS: Not Enabled
- **What:** The `tenant_id` column exists on all tables and is populated, but PostgreSQL Row-Level Security policies are NOT created in the migration. RLS requires a non-superuser DB role.
- **Replace with:** Create `nexo_app` role, `ENABLE ROW LEVEL SECURITY` + `CREATE POLICY` on all tables, switch app datasource to `nexo_app` role.
- **Effort:** Medium — SQL is straightforward but requires careful testing.

### CORS: Not Configured
- **Replace with:** Proper CORS config in Spring Security for the frontend domain.

### 8. Frontend: No Error Boundaries
- **All pages** — GraphQL errors and REST errors show as blank screens or console errors.
- **Replace with:** React Error Boundaries + Apollo error handling + toast notifications.

### 9. OntologyObjectEntity: No Hibernate vector column mapping
- **File:** `backend/src/main/java/com/nexoai/ontology/adapters/out/persistence/entity/OntologyObjectEntity.java`
- **What:** The `embedding` column is managed via raw JDBC (`JdbcTemplate`/`DataSource`), not mapped in the JPA entity.
- **Replace with:** Add a custom Hibernate `UserType` for pgvector vectors, or use the `com.pgvector:pgvector` Java library's Hibernate integration.
- **Effort:** Low-Medium.

---

## Phase 8 — Schema Versioning

### SchemaVersion not auto-created on ObjectType update
- **What:** `SchemaVersioningService.createVersion()` exists but is not yet called from the ObjectType update flow.
- **Replace with:** Call `createVersion()` inside `OntologyRegistryService.updateObjectType()` before applying changes.
- **Effort:** Low.

### Backfill: No batch parallelization
- **What:** `SchemaVersioningService.backfill()` iterates objects sequentially in a simple for-loop.
- **Replace with:** Use `Lists.partition(objects, 50)` + `parallelStream()` for batch processing.
- **Effort:** Low.

---

## Phase 9 — Real-Time CDC

### CDC Consumer: Simplified event processing
- **File:** `backend/src/main/java/com/nexoai/ontology/core/cdc/CdcEventConsumer.java`
- **What:** The `processRecord()` method only broadcasts via WebSocket. It does not yet map CDC events to OntologyObject upserts (needs DataSourceDefinition lookup by source table).
- **Replace with:** Implement full CDC→AntiCorruption→Upsert pipeline like in the phase spec (lookup connector config by table name, map via RecordMapper, upsert via OntologyObjectService).
- **Effort:** Medium.

### Debezium: Not configured for specific source databases
- **File:** `docker-compose.yml` (debezium service)
- **What:** Debezium service is defined but uses placeholder environment variables. Requires customer-specific database connection details.
- **Replace with:** Configure per-tenant source database connections. Consider Debezium Server REST API for dynamic connector management.
- **Effort:** Medium-High.

### Redis: Optional dependency
- **What:** Redis is excluded from autoconfig (`@SpringBootApplication(exclude = ...)`). The app starts without Redis, but CDC consumer won't work.
- **How to enable:** Set `nexo.cdc.enabled=true` in application.yml and start Redis (`docker compose up -d redis`).
- **Effort:** Low — just configuration.

---

## Phase 10 — AI Agent Layer

### Agent: Keyword-based tool routing (no LLM)
- **File:** `backend/src/main/java/com/nexoai/ontology/core/agent/AgentSessionService.java` → `processMessage()`
- **What:** The agent routes user messages to tools using simple keyword matching (`contains("suche")`, `contains("schema")`, etc.). No actual LLM is called.
- **Replace with:** Integrate Spring AI with OpenAI/Anthropic Claude for real NL understanding + function calling. The tool definitions in `OntologyAgentTools` are already structured for this.
- **Example integration:**
  ```java
  ChatClient chatClient = ChatClient.create(new OpenAiChatModel(new OpenAiApi(apiKey)));
  chatClient.prompt().system(systemPrompt).user(message)
      .functions("searchObjects", "traverseLinks", "aggregateObjects")
      .call().chatResponse();
  ```
- **Effort:** Medium — Spring AI setup + API key configuration + system prompt tuning.

### Agent: No multi-turn memory
- **What:** Each `agentChat()` call is independent. The session history is stored in DB but not sent to the LLM for context.
- **Replace with:** Load session history, build message array, pass to LLM as conversation context.
- **Effort:** Low once LLM is integrated.

### Agent: HITL only in DB, no WebSocket notification
- **What:** `PendingApproval` is stored in DB, but the frontend must poll for it. No push notification.
- **Replace with:** Use `WebSocketPublisher` to notify the frontend when an approval is created.
- **Effort:** Low.
