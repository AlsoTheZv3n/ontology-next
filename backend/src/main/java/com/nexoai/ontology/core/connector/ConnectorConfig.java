package com.nexoai.ontology.core.connector;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ConnectorConfig {
    private final UUID dataSourceId;
    private final ConnectorType type;
    private final JsonNode rawConfig;
    private final Map<String, String> columnMapping;
    private final UUID targetObjectTypeId;

    public String get(String key) {
        JsonNode node = rawConfig.get(key);
        return node != null ? node.asText() : null;
    }

    public String getOrDefault(String key, String defaultValue) {
        String val = get(key);
        return val != null ? val : defaultValue;
    }
}
