package com.nexoai.ontology.core.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictResolutionService {

    private final ObjectMapper objectMapper;

    public enum Strategy {
        LAST_WRITE_WINS, EXISTING_WINS, MERGE, SOURCE_PRIORITY
    }

    public JsonNode resolve(JsonNode existing, JsonNode incoming, Strategy strategy) {
        return switch (strategy) {
            case LAST_WRITE_WINS -> incoming;
            case EXISTING_WINS -> existing;
            case MERGE -> mergeProperties(existing, incoming);
            case SOURCE_PRIORITY -> incoming; // Simplified: incoming source wins
        };
    }

    private JsonNode mergeProperties(JsonNode existing, JsonNode incoming) {
        ObjectNode merged = existing.deepCopy().isObject()
                ? (ObjectNode) existing.deepCopy()
                : objectMapper.createObjectNode();

        incoming.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNull()) {
                merged.set(entry.getKey(), entry.getValue());
            }
        });

        return merged;
    }
}
