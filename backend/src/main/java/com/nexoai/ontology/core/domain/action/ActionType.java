package com.nexoai.ontology.core.domain.action;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionType {
    private UUID id;
    private String apiName;
    private String displayName;
    private UUID targetObjectTypeId;
    private JsonNode validationRules;
    private boolean requiresApproval;
    private JsonNode sideEffects;
    private String description;
    private Instant createdAt;

    public boolean hasSideEffect(String type) {
        if (sideEffects == null || !sideEffects.isArray()) return false;
        for (JsonNode se : sideEffects) {
            if (se.has("type") && se.get("type").asText().equals(type)) {
                return true;
            }
        }
        return false;
    }

    public JsonNode getSideEffectConfig(String type) {
        if (sideEffects == null || !sideEffects.isArray()) return null;
        for (JsonNode se : sideEffects) {
            if (se.has("type") && se.get("type").asText().equals(type)) {
                return se.get("config");
            }
        }
        return null;
    }
}
