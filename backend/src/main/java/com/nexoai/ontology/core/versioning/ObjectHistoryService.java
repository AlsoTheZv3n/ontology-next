package com.nexoai.ontology.core.versioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.adapters.out.persistence.entity.ObjectHistoryEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectHistoryRepository;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ObjectHistoryService {

    private final JpaObjectHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public void createSnapshot(UUID objectId, String properties, int schemaVersion,
                                String changeSource, String changedBy) {
        // Close current snapshot
        historyRepository.closeCurrentSnapshot(objectId, Instant.now());

        // Open new snapshot
        historyRepository.save(ObjectHistoryEntity.builder()
                .objectId(objectId)
                .tenantId(TenantContext.getTenantId())
                .schemaVersion(schemaVersion)
                .properties(properties)
                .txFrom(Instant.now())
                .validFrom(Instant.now())
                .changeSource(changeSource)
                .changedBy(changedBy)
                .build());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getObjectHistory(UUID objectId, int limit) {
        var entries = historyRepository.findByObjectIdOrderByTxFromDesc(objectId, PageRequest.of(0, limit));
        return entries.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.getId().toString());
            map.put("schemaVersion", e.getSchemaVersion());
            map.put("properties", parseJson(e.getProperties()));
            map.put("validFrom", e.getValidFrom());
            map.put("validTo", e.getValidTo());
            map.put("changeSource", e.getChangeSource() != null ? e.getChangeSource() : "unknown");
            map.put("changedBy", e.getChangedBy() != null ? e.getChangedBy() : "unknown");
            return map;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getObjectAsOf(UUID objectId, Instant asOf) {
        return historyRepository.findByObjectIdAndValidTime(objectId, asOf)
                .map(e -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", objectId.toString());
                    map.put("objectType", "");
                    map.put("properties", parseJson(e.getProperties()));
                    map.put("createdAt", e.getValidFrom());
                    map.put("updatedAt", e.getTxFrom());
                    return map;
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> diffObject(UUID objectId, Instant from, Instant to) {
        var beforeEntry = historyRepository.findByObjectIdAndValidTime(objectId, from);
        var afterEntry = historyRepository.findByObjectIdAndValidTime(objectId, to);

        JsonNode before = beforeEntry.map(e -> parseJson(e.getProperties())).orElse(objectMapper.createObjectNode());
        JsonNode after = afterEntry.map(e -> parseJson(e.getProperties())).orElse(objectMapper.createObjectNode());

        ObjectNode added = objectMapper.createObjectNode();
        ObjectNode removed = objectMapper.createObjectNode();
        ObjectNode changed = objectMapper.createObjectNode();

        // Find added and changed
        after.fieldNames().forEachRemaining(field -> {
            if (!before.has(field)) {
                added.set(field, after.get(field));
            } else if (!before.get(field).equals(after.get(field))) {
                ObjectNode diff = objectMapper.createObjectNode();
                diff.set("before", before.get(field));
                diff.set("after", after.get(field));
                changed.set(field, diff);
            }
        });

        // Find removed
        before.fieldNames().forEachRemaining(field -> {
            if (!after.has(field)) {
                removed.set(field, before.get(field));
            }
        });

        return Map.of("added", added, "removed", removed, "changed", changed);
    }

    private JsonNode parseJson(String json) {
        if (json == null) return objectMapper.createObjectNode();
        try { return objectMapper.readTree(json); }
        catch (Exception e) { return objectMapper.createObjectNode(); }
    }
}
