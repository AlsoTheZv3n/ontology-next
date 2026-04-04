package com.nexoai.ontology.core.domain.action;

import lombok.*;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ActionResult {
    private final boolean success;
    private final UUID objectId;
    private final String message;
    private final UUID auditLogId;

    public static ActionResult success(UUID objectId, UUID auditLogId) {
        return ActionResult.builder()
                .success(true).objectId(objectId).auditLogId(auditLogId).build();
    }

    public static ActionResult failed(String message, UUID auditLogId) {
        return ActionResult.builder()
                .success(false).message(message).auditLogId(auditLogId).build();
    }

    public static ActionResult pendingApproval(UUID auditLogId) {
        return ActionResult.builder()
                .success(false).message("Action requires approval")
                .auditLogId(auditLogId).build();
    }
}
