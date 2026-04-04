package com.nexoai.ontology.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.core.ml.SemanticSearchService;
import com.nexoai.ontology.core.service.object.OntologyObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Ontology-specific tools the AI agent can invoke.
 * Each tool returns a structured result the agent can interpret.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OntologyAgentTools {

    private final OntologyObjectService objectService;
    private final SemanticSearchService semanticSearchService;
    private final JpaObjectTypeRepository objectTypeRepository;

    public Map<String, Object> getOntologySchema() {
        var types = objectTypeRepository.findByIsActiveTrue();
        List<Map<String, Object>> schema = types.stream().map(ot -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("apiName", ot.getApiName());
            m.put("displayName", ot.getDisplayName());
            m.put("propertyCount", ot.getProperties().size());
            m.put("properties", ot.getProperties().stream()
                    .map(p -> p.getApiName() + ":" + p.getDataType()).toList());
            return m;
        }).toList();
        return Map.of("objectTypes", schema, "totalTypes", schema.size());
    }

    public Map<String, Object> searchObjects(String objectType, String query, int limit) {
        try {
            var results = semanticSearchService.search(query, objectType, limit, 0.0f);
            return Map.of(
                    "results", results.stream().map(r -> Map.of(
                            "id", r.id().toString(),
                            "similarity", r.similarity(),
                            "properties", r.properties()
                    )).toList(),
                    "count", results.size(),
                    "tool", "searchObjects"
            );
        } catch (Exception e) {
            // Fallback to regular search
            var page = objectService.searchObjects(objectType, limit, null);
            return Map.of(
                    "results", page.items().stream().map(o -> Map.of(
                            "id", o.getId().toString(),
                            "properties", o.getProperties()
                    )).toList(),
                    "count", page.totalCount(),
                    "tool", "searchObjects"
            );
        }
    }

    public Map<String, Object> traverseLinks(String objectId, String linkType, int depth) {
        var objects = objectService.traverseLinks(UUID.fromString(objectId), linkType, depth);
        return Map.of(
                "linkedObjects", objects.stream().map(o -> Map.of(
                        "id", o.getId().toString(),
                        "objectType", o.getObjectTypeName(),
                        "properties", o.getProperties()
                )).toList(),
                "count", objects.size(),
                "tool", "traverseLinks"
        );
    }

    public Map<String, Object> aggregateObjects(String objectType, String operation, String property) {
        var page = objectService.searchObjects(objectType, 1000, null);
        var items = page.items();

        return switch (operation.toUpperCase()) {
            case "COUNT" -> Map.of("result", items.size(), "operation", "COUNT", "objectType", objectType);
            case "SUM" -> {
                double sum = items.stream()
                        .mapToDouble(o -> {
                            JsonNode val = o.getProperties().get(property);
                            return val != null && val.isNumber() ? val.asDouble() : 0;
                        }).sum();
                yield Map.of("result", sum, "operation", "SUM", "property", property);
            }
            case "AVG" -> {
                var values = items.stream()
                        .map(o -> o.getProperties().get(property))
                        .filter(v -> v != null && v.isNumber())
                        .mapToDouble(JsonNode::asDouble).toArray();
                double avg = values.length > 0 ? Arrays.stream(values).average().orElse(0) : 0;
                yield Map.of("result", avg, "operation", "AVG", "property", property, "count", values.length);
            }
            default -> Map.of("error", "Unknown operation: " + operation);
        };
    }
}
