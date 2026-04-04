package com.nexoai.ontology.adapters.in.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.domain.action.ActionResult;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import com.nexoai.ontology.core.service.action.ActionEngine;
import com.nexoai.ontology.core.service.object.OntologyObjectService;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class OntologyMutationResolver {

    private final ActionEngine actionEngine;
    private final OntologyObjectService objectService;
    private final JpaObjectTypeRepository objectTypeRepository;
    private final ObjectMapper objectMapper;

    @MutationMapping
    public Map<String, Object> executeAction(@Argument String actionType,
                                              @Argument String objectId,
                                              @Argument Object params) {
        UUID objId = objectId != null ? UUID.fromString(objectId) : null;
        JsonNode paramsNode = objectMapper.valueToTree(params);

        ActionResult result = actionEngine.executeAction(actionType, objId, paramsNode, "anonymous");

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("objectId", result.getObjectId() != null ? result.getObjectId().toString() : null);
        response.put("message", result.getMessage());
        response.put("auditLogId", result.getAuditLogId().toString());
        return response;
    }

    @MutationMapping
    public Map<String, Object> createObject(@Argument String objectType,
                                             @Argument Object properties) {
        JsonNode propsNode = objectMapper.valueToTree(properties);
        OntologyObject created = objectService.createObject(objectType, propsNode);

        Map<String, Object> response = new HashMap<>();
        response.put("id", created.getId().toString());
        response.put("objectType", created.getObjectTypeName());
        response.put("properties", created.getProperties());
        response.put("createdAt", created.getCreatedAt());
        response.put("updatedAt", created.getUpdatedAt());
        return response;
    }

    @MutationMapping
    public boolean deleteObject(@Argument String objectType, @Argument String objectId) {
        return objectService.deleteObject(objectType, UUID.fromString(objectId));
    }

    @MutationMapping
    public Map<String, Object> registerActionType(@Argument Map<String, Object> input) {
        String apiName = (String) input.get("apiName");
        String displayName = (String) input.get("displayName");
        String targetObjectType = (String) input.get("targetObjectType");
        Boolean requiresApproval = input.get("requiresApproval") != null
                ? (Boolean) input.get("requiresApproval") : false;

        UUID targetTypeId = objectTypeRepository.findByIsActiveTrue().stream()
                .filter(ot -> ot.getApiName().equals(targetObjectType))
                .findFirst()
                .map(ot -> ot.getId())
                .orElseThrow(() -> new RuntimeException("ObjectType not found: " + targetObjectType));

        JsonNode validationRules = objectMapper.valueToTree(input.get("validationRules"));
        JsonNode sideEffects = objectMapper.valueToTree(input.get("sideEffects"));

        var actionType = actionEngine.registerActionType(apiName, displayName, targetTypeId,
                requiresApproval, validationRules, sideEffects, null);

        String targetName = objectTypeRepository.findById(actionType.getTargetObjectTypeId())
                .map(t -> t.getApiName()).orElse("unknown");

        return Map.of(
                "id", actionType.getId().toString(),
                "apiName", actionType.getApiName(),
                "displayName", actionType.getDisplayName(),
                "targetObjectType", targetName,
                "requiresApproval", actionType.isRequiresApproval(),
                "validationRules", actionType.getValidationRules()
        );
    }
}
