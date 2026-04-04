package com.nexoai.ontology.core.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.ActionLogEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaActionLogRepository;
import com.nexoai.ontology.core.domain.action.ActionLogEntry;
import com.nexoai.ontology.core.domain.action.ActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final JpaActionLogRepository actionLogRepository;
    private final ObjectMapper objectMapper;

    public UUID logAction(ActionType actionType, UUID objectId, String performedBy,
                          JsonNode beforeState, JsonNode afterState, JsonNode params, String status) {
        ActionLogEntity entity = ActionLogEntity.builder()
                .actionTypeId(actionType.getId())
                .objectId(objectId)
                .performedBy(performedBy)
                .status(status)
                .beforeState(beforeState != null ? beforeState.toString() : null)
                .afterState(afterState != null ? afterState.toString() : null)
                .params(params != null ? params.toString() : null)
                .build();
        return actionLogRepository.save(entity).getId();
    }

    public UUID logFailedAction(ActionType actionType, UUID objectId, String performedBy,
                                JsonNode params, String errorMessage) {
        ActionLogEntity entity = ActionLogEntity.builder()
                .actionTypeId(actionType.getId())
                .objectId(objectId)
                .performedBy(performedBy)
                .status("FAILED")
                .params(params != null ? params.toString() : null)
                .errorMessage(errorMessage)
                .build();
        return actionLogRepository.save(entity).getId();
    }

    public UUID logPendingAction(ActionType actionType, UUID objectId, String performedBy, JsonNode params) {
        ActionLogEntity entity = ActionLogEntity.builder()
                .actionTypeId(actionType.getId())
                .objectId(objectId)
                .performedBy(performedBy)
                .status("PENDING_APPROVAL")
                .params(params != null ? params.toString() : null)
                .build();
        return actionLogRepository.save(entity).getId();
    }

    public List<ActionLogEntry> getActionLog(UUID objectId, int limit) {
        var pageable = PageRequest.of(0, limit);
        List<ActionLogEntity> entities;
        if (objectId != null) {
            entities = actionLogRepository.findByObjectIdOrderByPerformedAtDesc(objectId, pageable);
        } else {
            entities = actionLogRepository.findAllByOrderByPerformedAtDesc(pageable);
        }
        return entities.stream().map(this::toDomain).toList();
    }

    private ActionLogEntry toDomain(ActionLogEntity entity) {
        return ActionLogEntry.builder()
                .id(entity.getId())
                .actionTypeId(entity.getActionTypeId())
                .objectId(entity.getObjectId())
                .performedBy(entity.getPerformedBy())
                .status(entity.getStatus())
                .beforeState(parseJson(entity.getBeforeState()))
                .afterState(parseJson(entity.getAfterState()))
                .params(parseJson(entity.getParams()))
                .errorMessage(entity.getErrorMessage())
                .performedAt(entity.getPerformedAt())
                .build();
    }

    private JsonNode parseJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
