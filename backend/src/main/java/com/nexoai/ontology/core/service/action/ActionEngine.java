package com.nexoai.ontology.core.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.ActionTypeEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaActionTypeRepository;
import com.nexoai.ontology.core.domain.action.*;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import com.nexoai.ontology.core.exception.OntologyException;
import com.nexoai.ontology.core.service.object.OntologyObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ActionEngine {

    private final JpaActionTypeRepository actionTypeRepository;
    private final ValidationService validationService;
    private final AuditService auditService;
    private final OntologyObjectService objectService;
    private final List<SideEffect> sideEffects;
    private final ObjectMapper objectMapper;

    public ActionResult executeAction(String actionTypeName, UUID objectId,
                                       JsonNode params, String performedBy) {
        // 1. ActionType laden
        ActionTypeEntity entity = actionTypeRepository.findByApiName(actionTypeName)
                .orElseThrow(() -> new OntologyException("ActionType not found: " + actionTypeName));
        ActionType actionType = toDomain(entity);

        // 2. Approval Check
        if (actionType.isRequiresApproval()) {
            UUID auditLogId = auditService.logPendingAction(actionType, objectId, performedBy, params);
            return ActionResult.pendingApproval(auditLogId);
        }

        // 3. Before-State laden
        OntologyObject object = null;
        JsonNode beforeState = null;
        if (objectId != null) {
            object = objectService.getObject(objectId);
            beforeState = object.getProperties();
        }

        // 4. Validation Rules pruefen
        List<ValidationRule> rules = parseValidationRules(actionType.getValidationRules());
        ValidationResult validation = validationService.validate(rules, params);
        if (!validation.isValid()) {
            String errorMsg = String.join("; ", validation.getErrors());
            UUID auditLogId = auditService.logFailedAction(actionType, objectId, performedBy, params, errorMsg);
            return ActionResult.failed(errorMsg, auditLogId);
        }

        // 5. Mutation ausfuehren
        JsonNode afterState;
        if (objectId != null) {
            OntologyObject updated = objectService.updateObjectProperties(objectId, params);
            afterState = updated.getProperties();
        } else {
            afterState = params;
        }

        // 6. Audit Log schreiben
        UUID auditLogId = auditService.logAction(actionType, objectId, performedBy,
                beforeState, afterState, params, "SUCCESS");

        // 7. Side-Effects async triggern
        OntologyObject finalObject = object;
        JsonNode finalAfterState = afterState;
        sideEffects.stream()
                .filter(se -> actionType.hasSideEffect(se.getType()))
                .forEach(se -> se.triggerAsync(actionType, finalObject, finalAfterState));

        log.info("Action {} executed successfully by {}", actionTypeName, performedBy);
        return ActionResult.success(objectId, auditLogId);
    }

    public ActionType registerActionType(String apiName, String displayName, UUID targetObjectTypeId,
                                          boolean requiresApproval, JsonNode validationRules,
                                          JsonNode sideEffectsConfig, String description) {
        if (actionTypeRepository.existsByApiName(apiName)) {
            throw new OntologyException("ActionType already exists: " + apiName);
        }

        ActionTypeEntity entity = ActionTypeEntity.builder()
                .apiName(apiName)
                .displayName(displayName)
                .targetObjectTypeId(targetObjectTypeId)
                .requiresApproval(requiresApproval)
                .validationRules(validationRules != null ? validationRules.toString() : "[]")
                .sideEffects(sideEffectsConfig != null ? sideEffectsConfig.toString() : "[]")
                .description(description)
                .build();
        var saved = actionTypeRepository.save(entity);
        return toDomain(saved);
    }

    @Transactional(readOnly = true)
    public List<ActionType> getActionTypes(UUID targetObjectTypeId) {
        List<ActionTypeEntity> entities;
        if (targetObjectTypeId != null) {
            entities = actionTypeRepository.findByTargetObjectTypeId(targetObjectTypeId);
        } else {
            entities = actionTypeRepository.findAll();
        }
        return entities.stream().map(this::toDomain).toList();
    }

    private ActionType toDomain(ActionTypeEntity entity) {
        return ActionType.builder()
                .id(entity.getId())
                .apiName(entity.getApiName())
                .displayName(entity.getDisplayName())
                .targetObjectTypeId(entity.getTargetObjectTypeId())
                .validationRules(parseJson(entity.getValidationRules()))
                .requiresApproval(entity.isRequiresApproval())
                .sideEffects(parseJson(entity.getSideEffects()))
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private List<ValidationRule> parseValidationRules(JsonNode rulesNode) {
        if (rulesNode == null || !rulesNode.isArray()) return List.of();
        List<ValidationRule> rules = new ArrayList<>();
        for (JsonNode node : rulesNode) {
            rules.add(ValidationRule.builder()
                    .property(node.path("property").asText())
                    .rule(node.path("rule").asText())
                    .errorMessage(node.path("errorMessage").asText())
                    .build());
        }
        return rules;
    }

    private JsonNode parseJson(String json) {
        if (json == null) return objectMapper.createArrayNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }
}
