package com.nexoai.ontology.core.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ObjectChangeEvent {
    private final String operation;
    private final UUID objectId;
    private final String objectType;
    private final JsonNode properties;
    private final Instant timestamp;

    public static ObjectChangeEvent upserted(UUID objectId, String objectType, JsonNode properties) {
        return ObjectChangeEvent.builder()
                .operation("UPSERTED").objectId(objectId).objectType(objectType)
                .properties(properties).timestamp(Instant.now()).build();
    }

    public static ObjectChangeEvent updated(UUID objectId, String objectType, JsonNode properties) {
        return ObjectChangeEvent.builder()
                .operation("UPDATED").objectId(objectId).objectType(objectType)
                .properties(properties).timestamp(Instant.now()).build();
    }

    public static ObjectChangeEvent deleted(UUID objectId, String objectType) {
        return ObjectChangeEvent.builder()
                .operation("DELETED").objectId(objectId).objectType(objectType)
                .timestamp(Instant.now()).build();
    }
}
