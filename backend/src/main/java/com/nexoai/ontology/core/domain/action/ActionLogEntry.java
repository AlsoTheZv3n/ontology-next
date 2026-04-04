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
public class ActionLogEntry {
    private UUID id;
    private UUID actionTypeId;
    private String actionTypeName;
    private UUID objectId;
    private String performedBy;
    private String status;
    private JsonNode beforeState;
    private JsonNode afterState;
    private JsonNode params;
    private String errorMessage;
    private Instant performedAt;
}
