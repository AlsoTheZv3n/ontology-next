package com.nexoai.ontology.core.versioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.adapters.out.persistence.entity.SchemaMigrationEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.SchemaVersionEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.*;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SchemaVersioningService {

    private final JpaSchemaVersionRepository versionRepository;
    private final JpaSchemaMigrationRepository migrationRepository;
    private final JpaOntologyObjectRepository objectRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create a new schema version snapshot.
     */
    public SchemaVersionEntity createVersion(UUID objectTypeId, JsonNode schemaSnapshot,
                                              String changeSummary, boolean isBreaking, String createdBy) {
        int nextVersion = versionRepository.findTopByObjectTypeIdOrderByVersionDesc(objectTypeId)
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        return versionRepository.save(SchemaVersionEntity.builder()
                .objectTypeId(objectTypeId)
                .tenantId(TenantContext.getTenantId())
                .version(nextVersion)
                .schemaSnapshot(schemaSnapshot.toString())
                .changeSummary(changeSummary)
                .isBreaking(isBreaking)
                .createdBy(createdBy)
                .build());
    }

    /**
     * Get all schema versions for an object type.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSchemaVersions(UUID objectTypeId) {
        return versionRepository.findByObjectTypeIdOrderByVersionDesc(objectTypeId).stream()
                .map(v -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("version", v.getVersion());
                    map.put("schemaSnapshot", parseJson(v.getSchemaSnapshot()));
                    map.put("changeSummary", v.getChangeSummary() != null ? v.getChangeSummary() : "");
                    map.put("isBreaking", v.isBreaking());
                    map.put("createdAt", v.getCreatedAt());
                    map.put("createdBy", v.getCreatedBy() != null ? v.getCreatedBy() : "system");
                    return map;
                })
                .toList();
    }

    /**
     * Analyze proposed schema changes.
     */
    public Map<String, Object> analyzeChange(JsonNode currentSchema, JsonNode proposedSchema) {
        List<Map<String, Object>> migrations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean isBreaking = false;

        Set<String> currentProps = new HashSet<>();
        Set<String> proposedProps = new HashSet<>();

        if (currentSchema.has("properties") && currentSchema.get("properties").isArray()) {
            for (JsonNode p : currentSchema.get("properties")) {
                currentProps.add(p.get("apiName").asText());
            }
        }
        if (proposedSchema.has("properties") && proposedSchema.get("properties").isArray()) {
            for (JsonNode p : proposedSchema.get("properties")) {
                proposedProps.add(p.get("apiName").asText());
            }
        }

        // Removed properties
        for (String prop : currentProps) {
            if (!proposedProps.contains(prop)) {
                migrations.add(Map.of(
                        "type", "REMOVE",
                        "sourceProperty", prop,
                        "description", "Property '" + prop + "' will be removed"
                ));
                warnings.add("BREAKING: Property '" + prop + "' will be removed. Existing data will be lost.");
                isBreaking = true;
            }
        }

        // Added properties
        for (String prop : proposedProps) {
            if (!currentProps.contains(prop)) {
                migrations.add(Map.of(
                        "type", "ADD",
                        "targetProperty", prop,
                        "description", "Property '" + prop + "' will be added"
                ));
            }
        }

        long affectedCount = objectRepository.findAll().stream()
                .filter(o -> currentProps.stream().anyMatch(p -> {
                    try {
                        JsonNode props = objectMapper.readTree(o.getProperties());
                        return props.has(p);
                    } catch (Exception e) { return false; }
                }))
                .count();

        return Map.of(
                "migrations", migrations,
                "warnings", warnings,
                "isBreaking", isBreaking,
                "affectedObjectCount", affectedCount
        );
    }

    /**
     * Backfill: apply migration transformations to all objects of a type.
     * Uses batched parallelStream for improved throughput on large datasets (Fix 08).
     */
    @Async
    public CompletableFuture<Map<String, Object>> backfill(UUID objectTypeId, int fromVersion, int toVersion) {
        var migrations = migrationRepository.findByObjectTypeIdAndFromVersionAndToVersion(
                objectTypeId, fromVersion, toVersion);

        var objects = objectRepository.findAll().stream()
                .filter(o -> o.getObjectTypeId().equals(objectTypeId))
                .toList();

        int batchSize = 100;
        java.util.concurrent.atomic.AtomicInteger migrated = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < objects.size(); i += batchSize) {
            int end = Math.min(i + batchSize, objects.size());
            List<OntologyObjectEntity> batch = objects.subList(i, end);

            batch.parallelStream().forEach(obj -> {
                try {
                    JsonNode props = objectMapper.readTree(obj.getProperties());
                    JsonNode migratedProps = applyMigrations(props, migrations);
                    obj.setProperties(migratedProps.toString());
                    obj.setSchemaVersion(toVersion);
                    objectRepository.save(obj);
                    migrated.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Backfill failed for object {}: {}", obj.getId(), e.getMessage());
                    failed.incrementAndGet();
                }
            });
        }

        log.info("Backfill complete: {} migrated, {} failed", migrated.get(), failed.get());
        return CompletableFuture.completedFuture(Map.of("migrated", migrated.get(), "failed", failed.get()));
    }

    private JsonNode applyMigrations(JsonNode properties, List<SchemaMigrationEntity> migrations) {
        ObjectNode result = (ObjectNode) properties.deepCopy();

        for (SchemaMigrationEntity m : migrations) {
            switch (m.getMigrationType()) {
                case "RENAME" -> {
                    JsonNode value = result.remove(m.getSourceProperty());
                    if (value != null) result.set(m.getTargetProperty(), value);
                }
                case "ADD" -> {
                    if (!result.has(m.getTargetProperty())) {
                        result.putNull(m.getTargetProperty());
                    }
                }
                case "REMOVE" -> result.remove(m.getSourceProperty());
            }
        }
        return result;
    }

    private JsonNode parseJson(String json) {
        if (json == null) return objectMapper.createObjectNode();
        try { return objectMapper.readTree(json); }
        catch (Exception e) { return objectMapper.createObjectNode(); }
    }
}
