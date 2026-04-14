package com.nexoai.ontology.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.core.agent.llm.LlmToolDefinition;
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

    /**
     * JSON-Schema tool definitions exposed to the LLM for function-calling.
     */
    public List<LlmToolDefinition> toolDefinitions() {
        return List.of(
                new LlmToolDefinition(
                        "getOntologySchema",
                        "Returns all active object types in the ontology with their properties. Call this FIRST to understand what data is available before querying.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "searchObjects",
                        "Semantic search for objects of a given type. Use when user asks to find/show/list objects matching a natural-language description.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "objectType", Map.of("type", "string", "description", "API name of the object type (e.g. 'Customer')"),
                                        "query", Map.of("type", "string", "description", "Free-text search query"),
                                        "limit", Map.of("type", "integer", "description", "Max results", "default", 10)
                                ),
                                "required", List.of("objectType", "query")
                        )
                ),
                new LlmToolDefinition(
                        "aggregateObjects",
                        "Compute an aggregation (COUNT, SUM, AVG) across all objects of a type. Use when user asks 'how many', 'total', 'average', etc.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "objectType", Map.of("type", "string", "description", "API name of the object type"),
                                        "operation", Map.of("type", "string", "enum", List.of("COUNT", "SUM", "AVG")),
                                        "property", Map.of("type", "string", "description", "Property name for SUM/AVG (ignored for COUNT)")
                                ),
                                "required", List.of("objectType", "operation")
                        )
                ),
                new LlmToolDefinition(
                        "traverseLinks",
                        "Follow relationship links from a source object to find connected objects. Use for questions like 'which orders belong to customer X'.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "startObjectId", Map.of("type", "string", "description", "UUID of the source object"),
                                        "linkType", Map.of("type", "string", "description", "API name of the link type"),
                                        "depth", Map.of("type", "integer", "description", "How many hops to traverse", "default", 1)
                                ),
                                "required", List.of("startObjectId", "linkType")
                        )
                )
        );
    }

    /**
     * Central dispatcher. Called by agent loop when LLM requests a tool.
     */
    public Object executeTool(String name, Map<String, Object> input) {
        log.info("Agent tool invocation: {} with args: {}", name, input);
        return switch (name) {
            case "getOntologySchema" -> getOntologySchema();
            case "searchObjects" -> searchObjects(
                    (String) input.get("objectType"),
                    (String) input.getOrDefault("query", ""),
                    intArg(input, "limit", 10));
            case "aggregateObjects" -> aggregateObjects(
                    (String) input.get("objectType"),
                    (String) input.get("operation"),
                    (String) input.getOrDefault("property", "id"));
            case "traverseLinks" -> traverseLinks(
                    (String) input.get("startObjectId"),
                    (String) input.get("linkType"),
                    intArg(input, "depth", 1));
            default -> Map.of("error", "Unknown tool: " + name);
        };
    }

    private int intArg(Map<String, Object> input, String key, int fallback) {
        Object v = input.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }
}
