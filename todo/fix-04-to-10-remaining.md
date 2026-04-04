# Fix 04 — CDC Consumer: Vollständige Upsert-Pipeline

**Priorität:** 🔴 Kritisch  
**Aufwand:** Medium  
**Status:** 🔲 Offen

---

## Problem

`CdcEventConsumer.processRecord()` broadcastet CDC-Events nur via WebSocket. Das eigentliche Ziel — OntologyObjects in der Ontology aktualisieren — fehlt komplett.

---

## File

```
backend/src/main/java/com/nexoai/ontology/core/cdc/CdcEventConsumer.java
```

---

## Fix

```java
@Component
@Slf4j
public class CdcEventConsumer {

    private final DataSourceDefinitionRepository dataSourceRepository;
    private final RecordMapper recordMapper;
    private final OntologyObjectService objectService;
    private final ConflictResolutionService conflictResolution;
    private final WebSocketPublisher wsPublisher;
    private final ObjectMapper objectMapper;

    // Bestehende Methode erweitern
    private void processRecord(MapRecord<String, Object, Object> record) throws Exception {
        CdcEvent event = parseCdcEvent(record);

        // 1. Connector-Config für diese Tabelle finden
        Optional<DataSourceDefinition> sourceOpt = dataSourceRepository
            .findBySourceTable(event.table());

        if (sourceOpt.isEmpty()) {
            log.debug("No connector for table '{}' — skipping CDC event", event.table());
            return;
        }

        DataSourceDefinition source = sourceOpt.get();
        ConnectorConfig config = ConnectorConfig.from(source);

        // 2. Je nach Operation: Upsert oder Delete
        switch (event.operation()) {

            case "c", "r", "u" -> {
                Map<String, Object> rowData = event.after();
                String externalId = extractId(rowData, config);

                // Anti-Corruption: Row → RawRecord → OntologyObject
                RawRecord raw = new RawRecord(rowData, externalId);
                OntologyObject incoming = recordMapper.toOntologyObject(raw, config);

                // Upsert mit Conflict Resolution
                Optional<OntologyObject> existing = objectService
                    .findByExternalId(externalId, source.getObjectTypeId(), source.getTenantId());

                if (existing.isPresent()) {
                    ConflictResolution resolution = conflictResolution.resolve(
                        existing.get(), incoming, source
                    );
                    objectService.update(existing.get().getId(), resolution.getWinnerProperties());
                    wsPublisher.publish(source.getTenantId(),
                        ObjectChangeEvent.updated(existing.get().getId(), resolution.getWinnerProperties()));
                } else {
                    OntologyObject created = objectService.create(incoming);
                    wsPublisher.publish(source.getTenantId(), ObjectChangeEvent.created(created));
                }
            }

            case "d" -> {
                String externalId = extractId(event.before(), config);
                objectService.findByExternalId(externalId, source.getObjectTypeId(), source.getTenantId())
                    .ifPresent(obj -> {
                        objectService.softDelete(obj.getId());
                        wsPublisher.publish(source.getTenantId(), ObjectChangeEvent.deleted(obj.getId()));
                    });
            }

            default -> log.warn("Unknown CDC operation: {}", event.operation());
        }
    }

    private String extractId(Map<String, Object> row, ConnectorConfig config) {
        String idColumn = config.get("idColumn");
        Object id = row.get(idColumn);
        if (id == null) throw new CdcException("ID column '" + idColumn + "' not found in CDC event");
        return id.toString();
    }
}
```

---

## Akzeptanzkriterien

- [ ] INSERT in Source-DB → neues OntologyObject in Ontology (< 5 Sekunden)
- [ ] UPDATE in Source-DB → bestehendes Object aktualisiert
- [ ] DELETE in Source-DB → Object soft-deleted
- [ ] WebSocket Push an Frontend bei jeder Änderung
- [ ] Tabellen ohne Connector-Config werden still ignoriert (kein Fehler)

---

---

# Fix 05 — Echte Embeddings (DJL Sentence Transformers)

**Priorität:** 🟡 Wichtig  
**Aufwand:** Medium  
**Status:** 🔲 Offen

---

## Problem

`EmbeddingService` generiert Pseudo-Embeddings aus Token-IDs. Semantic Search gibt bedeutungslose Similarity-Scores (~0.08). Braucht echten Transformer für sinnvolle Werte (0.5–0.95).

---

## File

```
backend/src/main/java/com/nexoai/ontology/core/ml/EmbeddingService.java
```

---

## Fix: Variante A — ONNX Runtime (empfohlen, kein PyTorch nötig)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>ai.djl.onnxruntime</groupId>
    <artifactId>onnxruntime-engine</artifactId>
    <version>0.26.0</version>
</dependency>
<dependency>
    <groupId>ai.djl.huggingface</groupId>
    <artifactId>tokenizers</artifactId>
    <version>0.26.0</version>
</dependency>
```

```java
@Service
@Slf4j
public class EmbeddingService {

    private static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";
    private static final int EMBEDDING_DIM = 384;

    private Predictor<String[], float[][]> predictor;

    @PostConstruct
    public void init() {
        try {
            log.info("Loading embedding model: {}", MODEL_NAME);

            // ONNX Export des Modells von HuggingFace laden
            Criteria<String[], float[][]> criteria = Criteria.builder()
                .setTypes(String[].class, float[][].class)
                .optModelUrls("djl://ai.djl.huggingface.onnxruntime/" + MODEL_NAME)
                .optTranslator(new SentenceTransformerTranslator())
                .optProgress(new ProgressBar())
                .build();

            ZooModel<String[], float[][]> model = ModelZoo.loadModel(criteria);
            this.predictor = model.newPredictor();

            log.info("Embedding model loaded successfully. Dimension: {}", EMBEDDING_DIM);
        } catch (Exception e) {
            log.error("Failed to load embedding model — falling back to pseudo-embeddings", e);
            // Graceful Degradation: PoC-Implementierung bleibt als Fallback
        }
    }

    public float[] embed(String text) {
        if (predictor == null) {
            log.warn("Embedding model not loaded — using hash fallback");
            return hashEmbedding(text);
        }

        try {
            String normalized = normalize(text);
            float[][] result = predictor.predict(new String[]{normalized});
            return result[0];
        } catch (TranslateException e) {
            log.error("Embedding failed: {}", e.getMessage());
            return hashEmbedding(text);
        }
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ")
            .strip()
            .substring(0, Math.min(text.length(), 512));
    }

    // Fallback bleibt erhalten
    private float[] hashEmbedding(String text) { /* bestehende Impl */ }
}
```

### SentenceTransformerTranslator.java

```java
public class SentenceTransformerTranslator implements NoBatchifyTranslator<String[], float[][]> {

    private HuggingFaceTokenizer tokenizer;

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        tokenizer = HuggingFaceTokenizer.newInstance("sentence-transformers/all-MiniLM-L6-v2",
            Map.of("padding", "true", "truncation", "true", "maxLength", "512"));
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String[] inputs) {
        Encoding[] encodings = tokenizer.batchEncode(inputs);
        NDManager manager = ctx.getNDManager();

        long[][] inputIds = Arrays.stream(encodings).map(Encoding::getIds).toArray(long[][]::new);
        long[][] attentionMask = Arrays.stream(encodings).map(Encoding::getAttentionMask).toArray(long[][]::new);

        return new NDList(
            manager.create(inputIds),
            manager.create(attentionMask)
        );
    }

    @Override
    public float[][] processOutput(TranslatorContext ctx, NDList list) {
        // Mean Pooling über alle Token-Embeddings
        NDArray tokenEmbeddings = list.get(0);
        NDArray meanPooled = tokenEmbeddings.mean(new int[]{1});

        // L2-Normalisierung
        NDArray norms = meanPooled.norm(new int[]{1}, true);
        NDArray normalized = meanPooled.div(norms.add(1e-9));

        return normalized.toFloatArray2D();
    }
}
```

---

## Fix: Variante B — Anthropic/OpenAI Embeddings API (einfacher)

Falls DJL zu komplex ist, externe Embedding-API nutzen:

```java
@Service
@ConditionalOnProperty(name = "nexo.ml.embedding-provider", havingValue = "anthropic-voyage")
public class VoyageEmbeddingService implements EmbeddingService {

    private final WebClient webClient;

    public VoyageEmbeddingService(@Value("${nexo.ml.voyage-api-key}") String apiKey) {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.voyageai.com/v1")
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
    }

    @Override
    public float[] embed(String text) {
        Map<String, Object> response = webClient.post()
            .uri("/embeddings")
            .bodyValue(Map.of("input", text, "model", "voyage-3"))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        List<Double> embedding = (List<Double>) ((List<?>) ((Map<?,?>) ((List<?>) response.get("data")).get(0)).get("embedding"));
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) result[i] = embedding.get(i).floatValue();
        return result;
    }
}
```

---

## application.yml

```yaml
nexo:
  ml:
    embedding-provider: ${NEXO_EMBEDDING_PROVIDER:djl-onnx}   # djl-onnx | voyage | openai
    voyage-api-key: ${VOYAGE_API_KEY:}
    model-cache-dir: ${MODEL_CACHE_DIR:/tmp/nexo-models}
```

---

## Akzeptanzkriterien

- [ ] Similarity zweier ähnlicher Texte > 0.7
- [ ] Similarity zweier komplett unterschiedlicher Texte < 0.3
- [ ] Embedding-Generierung < 500ms pro Object
- [ ] Modell wird beim ersten Start gecachet (`/tmp/nexo-models`)
- [ ] Fallback auf Hash-Embedding wenn Modell nicht lädt (kein Crash)

---

---

# Fix 06 — pgvector Hibernate Mapping

**Priorität:** 🟡 Wichtig  
**Aufwand:** Low-Medium  
**Status:** 🔲 Offen

---

## Problem

`embedding` Spalte in `OntologyObjectEntity` ist nicht als JPA-Feld gemappt. Embedding-Updates laufen über raw JDBC — nicht konsistent mit dem Rest.

---

## Files

```
backend/src/main/java/com/nexoai/ontology/adapters/out/persistence/entity/OntologyObjectEntity.java
backend/src/main/java/com/nexoai/ontology/adapters/out/persistence/type/VectorType.java  ← neu
```

---

## Fix: VectorType.java (Custom Hibernate UserType)

```java
package com.nexoai.ontology.adapters.out.persistence.type;

public class VectorType implements UserType<float[]> {

    @Override
    public int getSqlType() { return Types.OTHER; }

    @Override
    public Class<float[]> returnedClass() { return float[].class; }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject pgVector = new PGobject();
            pgVector.setType("vector");
            pgVector.setValue(Arrays.toString(value)
                .replace(" ", ""));       // [0.1,0.2,...] Format
            st.setObject(index, pgVector);
        }
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position,
                               SharedSessionContractImplementor session,
                               Object owner) throws SQLException {
        String value = rs.getString(position);
        if (value == null) return null;

        // PostgreSQL gibt "[0.1,0.2,...]" zurück
        String[] parts = value.substring(1, value.length() - 1).split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    @Override
    public boolean equals(float[] a, float[] b) { return Arrays.equals(a, b); }

    @Override
    public int hashCode(float[] x) { return Arrays.hashCode(x); }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean isMutable() { return true; }

    @Override
    public Serializable disassemble(float[] value) { return value; }

    @Override
    public float[] assemble(Serializable cached, Object owner) { return (float[]) cached; }
}
```

## Fix: OntologyObjectEntity.java

```java
@Entity
@Table(name = "objects")
public class OntologyObjectEntity {

    // ... bestehende Felder ...

    // NEU: Embedding als JPA-Feld
    @Column(name = "embedding", columnDefinition = "vector(384)")
    @Type(VectorType.class)
    private float[] embedding;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "embedded_at")
    private Instant embeddedAt;
}
```

---

## Akzeptanzkriterien

- [ ] `OntologyObjectEntity.embedding` ist JPA-Feld (kein raw JDBC mehr)
- [ ] Save/Load eines Objects mit Embedding funktioniert korrekt
- [ ] pgvector `<=>` Query funktioniert über JPA native Query

---

---

# Fix 07 — Schema Versioning verdrahten

**Priorität:** 🟡 Wichtig  
**Aufwand:** Low  
**Status:** 🔲 Offen

---

## Problem

`SchemaVersioningService.createVersion()` existiert aber wird nie aufgerufen wenn ein ObjectType geändert wird.

---

## File

```
backend/src/main/java/com/nexoai/ontology/core/service/OntologyRegistryService.java
```

---

## Fix

```java
@Service
@Transactional
public class OntologyRegistryService {

    private final SchemaVersioningService schemaVersioningService;

    public ObjectType updateObjectType(UUID id, UpdateObjectTypeCommand command) {
        ObjectType current = objectTypeRepository.findById(id)
            .orElseThrow(() -> new ObjectTypeNotFoundException(id));

        // Schema-Analyse: Breaking Changes erkennen
        SchemaChangeAnalysis analysis = schemaVersioningService.analyzeChange(current, command);

        if (analysis.isBreaking()) {
            log.warn("Breaking schema change on '{}': {}", current.getApiName(), analysis.getWarnings());
            // Optional: Event publishen für Notification
        }

        // Version VOR der Änderung speichern
        schemaVersioningService.createVersion(
            current,
            analysis.getMigrations(),
            analysis.isBreaking(),
            buildChangeSummary(analysis)
        );

        // Änderung anwenden
        ObjectType updated = applyUpdate(current, command);
        objectTypeRepository.save(updated);

        // Backfill asynchron starten (wenn Breaking Change)
        if (analysis.isBreaking() && !analysis.getMigrations().isEmpty()) {
            schemaVersioningService.backfillAsync(id, current.getVersion(), updated.getVersion());
        }

        return updated;
    }

    private String buildChangeSummary(SchemaChangeAnalysis analysis) {
        return analysis.getMigrations().stream()
            .map(m -> switch (m.getMigrationType()) {
                case ADD -> "Added '" + m.getTargetProperty() + "'";
                case REMOVE -> "Removed '" + m.getSourceProperty() + "'";
                case RENAME -> "Renamed '" + m.getSourceProperty() + "' → '" + m.getTargetProperty() + "'";
                case TYPE_CHANGE -> "Type change on '" + m.getSourceProperty() + "'";
            })
            .collect(Collectors.joining(", "));
    }
}
```

---

## Akzeptanzkriterien

- [ ] `PUT /api/v1/ontology/object-types/{id}` erstellt automatisch einen `SchemaVersion` Eintrag
- [ ] `GET /graphql → getSchemaVersions("Customer")` zeigt die Version-History
- [ ] Breaking Change → Backfill startet asynchron

---

---

# Fix 08 — Backfill Parallelisierung

**Priorität:** 🟡 Wichtig  
**Aufwand:** Low  
**Status:** 🔲 Offen

---

## Problem

`SchemaVersioningService.backfill()` verarbeitet Objects sequenziell in einem einfachen For-Loop.

---

## File

```
backend/src/main/java/com/nexoai/ontology/core/versioning/SchemaVersioningService.java
```

---

## Fix

```java
@Async("mlExecutor")  // Thread-Pool aus MLConfig
public CompletableFuture<BackfillResult> backfillAsync(UUID objectTypeId, int fromVersion, int toVersion) {
    List<SchemaMigration> migrations = migrationRepository
        .findByObjectTypeAndVersionRange(objectTypeId, fromVersion, toVersion);

    List<UUID> objectIds = objectRepository.findIdsByObjectTypeId(objectTypeId);

    log.info("Starting backfill: {} objects, {} migrations", objectIds.size(), migrations.size());

    AtomicInteger migrated = new AtomicInteger(0);
    AtomicInteger failed = new AtomicInteger(0);

    // In Chunks von 50 aufteilen + parallel verarbeiten
    Lists.partition(objectIds, 50).forEach(chunk -> {
        chunk.parallelStream().forEach(objectId -> {
            try {
                OntologyObject obj = objectRepository.findById(objectId).orElseThrow();
                JsonNode migrated = applyMigrations(obj.getProperties(), migrations);
                objectRepository.updatePropertiesAndVersion(objectId, migrated, toVersion);
                migrated.incrementAndGet();
            } catch (Exception e) {
                log.warn("Backfill failed for {}: {}", objectId, e.getMessage());
                failed.incrementAndGet();
            }
        });
    });

    log.info("Backfill complete: {} migrated, {} failed", migrated.get(), failed.get());
    return CompletableFuture.completedFuture(new BackfillResult(migrated.get(), failed.get()));
}
```

---

## Akzeptanzkriterien

- [ ] 10.000 Objects Backfill in unter 60 Sekunden
- [ ] Fehler in einzelnen Objects stoppen nicht den gesamten Backfill
- [ ] Backfill-Progress loggbar (Anzahl migrated / failed)

---

---

# Fix 09 — SemanticSearch: Parameterized Query

**Priorität:** 🟡 Wichtig  
**Aufwand:** Low  
**Status:** 🔲 Offen

---

## Problem

String-Konkatenation für pgvector SQL Query — kein SQL-Injection Risiko (Embeddings kommen serverseitig), aber kein Best Practice.

---

## File

```
backend/src/main/java/com/nexoai/ontology/core/ml/SemanticSearchService.java
backend/src/main/java/com/nexoai/ontology/adapters/out/persistence/OntologyObjectJpaAdapter.java
```

---

## Fix

```java
// In OntologyObjectJpaAdapter.java
@Query(value = """
    SELECT
        o.id,
        o.object_type_id,
        o.properties,
        o.tenant_id,
        o.created_at,
        1 - (o.embedding <=> CAST(:embedding AS vector)) AS similarity
    FROM objects o
    INNER JOIN object_types ot ON o.object_type_id = ot.id
    WHERE ot.api_name = :objectType
      AND o.tenant_id = :tenantId
      AND o.embedding IS NOT NULL
      AND o.deleted_at IS NULL
      AND 1 - (o.embedding <=> CAST(:embedding AS vector)) >= :minSimilarity
    ORDER BY o.embedding <=> CAST(:embedding AS vector)
    LIMIT :limit
    """, nativeQuery = true)
List<ObjectWithSimilarityProjection> findBySemanticSimilarity(
    @Param("embedding") String embedding,   // Als String: "[0.1,0.2,...]"
    @Param("objectType") String objectType,
    @Param("tenantId") UUID tenantId,
    @Param("limit") int limit,
    @Param("minSimilarity") float minSimilarity
);
```

```java
// SemanticSearchService: float[] → String konvertieren
private String embeddingToString(float[] embedding) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
        if (i > 0) sb.append(",");
        sb.append(embedding[i]);
    }
    sb.append("]");
    return sb.toString();
}
```

---

## Akzeptanzkriterien

- [ ] Query nutzt `@Param` statt String-Konkatenation
- [ ] Embedding-Konvertierung korrekt (`float[]` → `"[0.1,0.2,...]"`)
- [ ] Semantic Search gibt korrekte Ergebnisse zurück

---

---

# Fix 10 — Production Hardening

**Priorität:** 🟢 Nice-to-have  
**Aufwand:** Low  
**Status:** 🔲 Offen

---

## 10a — Webhook Retry + Dead Letter Queue

**File:** `WebhookSideEffect.java`, `N8nSideEffect.java`

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
```

```java
@Retryable(
    retryFor = WebClientException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)   // 1s, 2s, 4s
)
@Async
public void triggerAsync(ActionType actionType, OntologyObject object, JsonNode newState) {
    // ... bestehende Logik ...
}

@Recover
public void recoverWebhook(WebClientException ex, ActionType actionType, ...) {
    log.error("Webhook permanently failed after retries: {}", ex.getMessage());
    // In Dead Letter Tabelle speichern
    deadLetterRepository.save(new DeadLetterEntry(actionType, object, newState, ex.getMessage()));
}
```

---

## 10b — n8n Idempotency

**File:** `N8nInboundController.java`

```java
@PostMapping("/objects/{objectType}")
public ResponseEntity<?> createObjectFromN8n(
    @PathVariable String objectType,
    @RequestBody Map<String, Object> payload,
    @RequestHeader("X-N8N-Webhook-Secret") String secret
) {
    validateWebhookSecret(secret);

    // externalId für Upsert nutzen
    String externalId = (String) payload.get("externalId");
    if (externalId == null) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Missing 'externalId' field for idempotent upsert"));
    }

    OntologyObject result = objectService.upsertByExternalId(objectType, externalId, payload);
    return ResponseEntity.ok(result);
}
```

---

## 10c — CORS konfigurieren

Bereits in Fix 01 (`SecurityConfig.java`) enthalten — `corsConfigurationSource()` Bean.

---

## 10d — Frontend Error Boundaries

**File:** `frontend/src/App.tsx`

```tsx
// frontend/src/components/shared/ErrorBoundary.tsx
export class ErrorBoundary extends React.Component<
  { children: ReactNode; fallback?: ReactNode },
  { hasError: boolean; error?: Error }
> {
  state = { hasError: false }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error }
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback ?? (
        <div className="flex flex-col items-center justify-center h-64 gap-4">
          <AlertTriangle className="h-12 w-12 text-destructive" />
          <p className="font-medium">Etwas ist schiefgelaufen</p>
          <p className="text-sm text-muted-foreground">{this.state.error?.message}</p>
          <Button onClick={() => this.setState({ hasError: false })}>
            Nochmals versuchen
          </Button>
        </div>
      )
    }
    return this.props.children
  }
}

// App.tsx: Jede Page in ErrorBoundary wrappen
<ErrorBoundary>
  <Routes>
    <Route path="/" element={<Dashboard />} />
    ...
  </Routes>
</ErrorBoundary>
```

---

## 10e — Agent Multi-Turn Memory

**File:** `AgentSessionService.java`

```java
// History laden und an LLM senden
private List<LlmMessage> loadHistory(UUID sessionId) {
    return sessionRepository.findBySessionId(sessionId)
        .map(session -> {
            List<LlmMessage> history = new ArrayList<>();
            for (AgentMessage msg : session.getMessages()) {
                history.add(msg.getRole() == Role.USER
                    ? LlmMessage.user(msg.getContent())
                    : LlmMessage.assistant(msg.getContent()));
            }
            // Auf die letzten 20 Messages begrenzen (Context-Window Management)
            return history.size() > 20
                ? history.subList(history.size() - 20, history.size())
                : history;
        })
        .orElse(new ArrayList<>());
}
```

---

## 10f — HITL WebSocket Push

**File:** `ActionEngine.java` + `WebSocketPublisher.java`

```java
// Wenn PendingApproval erstellt wird → WebSocket Push
public ActionResult executeAction(ExecuteActionCommand command) {
    // ...
    if (actionType.isRequiresApproval()) {
        UUID approvalId = auditService.logPendingAction(actionType, command);

        // NEU: WebSocket Push an Frontend
        wsPublisher.publish(TenantContext.getTenantId(),
            ApprovalRequestEvent.of(approvalId, actionType.getApiName(),
                command.objectId(), command.params()));

        return ActionResult.pendingApproval(approvalId);
    }
    // ...
}
```

---

## Akzeptanzkriterien für Fix 10

- [ ] Webhook-Failures werden nach 3 Versuchen in Dead Letter Tabelle gespeichert
- [ ] n8n Bulk-Import mit gleichem `externalId` erstellt kein Duplikat
- [ ] Frontend zeigt Error-Boundary statt weissen Screen bei GraphQL-Fehler
- [ ] Agent-Chat erinnert sich an die letzten 20 Nachrichten in derselben Session
- [ ] HITL Approval erscheint im Frontend ohne Polling (via WebSocket Push)
