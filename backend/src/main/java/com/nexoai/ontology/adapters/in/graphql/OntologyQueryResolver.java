package com.nexoai.ontology.adapters.in.graphql;

import com.nexoai.ontology.adapters.out.persistence.mapper.ObjectTypeMapper;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaLinkTypeRepository;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import com.nexoai.ontology.core.service.action.ActionEngine;
import com.nexoai.ontology.core.service.action.AuditService;
import com.nexoai.ontology.core.service.object.OntologyObjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class OntologyQueryResolver {

    private final OntologyObjectService objectService;
    private final JpaObjectTypeRepository objectTypeRepository;
    private final JpaLinkTypeRepository linkTypeRepository;
    private final ObjectTypeMapper objectTypeMapper;
    private final ActionEngine actionEngine;
    private final AuditService auditService;

    @QueryMapping
    public Map<String, Object> searchObjects(@Argument String objectType,
                                              @Argument Map<String, Object> filter,
                                              @Argument Map<String, Object> pagination) {
        int limit = 20;
        String cursor = null;
        if (pagination != null) {
            if (pagination.get("limit") != null) limit = (int) pagination.get("limit");
            cursor = (String) pagination.get("cursor");
        }

        var page = objectService.searchObjects(objectType, limit, cursor);
        return Map.of(
                "items", page.items().stream().map(this::objectToMap).toList(),
                "totalCount", page.totalCount(),
                "hasNextPage", page.hasNextPage(),
                "cursor", page.cursor() != null ? page.cursor() : ""
        );
    }

    @QueryMapping
    public Map<String, Object> getObject(@Argument String id) {
        OntologyObject obj = objectService.getObject(UUID.fromString(id));
        return objectToMap(obj);
    }

    @QueryMapping
    public List<Map<String, Object>> traverseLinks(@Argument String objectId,
                                                     @Argument String linkType,
                                                     @Argument Integer depth) {
        int d = depth != null ? depth : 1;
        return objectService.traverseLinks(UUID.fromString(objectId), linkType, d)
                .stream().map(this::objectToMap).toList();
    }

    @QueryMapping
    public Map<String, Object> getObjectType(@Argument String apiName) {
        var entity = objectTypeRepository.findByIsActiveTrue().stream()
                .filter(ot -> ot.getApiName().equals(apiName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("ObjectType not found: " + apiName));
        return objectTypeSchemaToMap(entity);
    }

    @QueryMapping
    public List<Map<String, Object>> getAllObjectTypes() {
        return objectTypeRepository.findByIsActiveTrue().stream()
                .map(this::objectTypeSchemaToMap)
                .toList();
    }

    @QueryMapping
    public List<Map<String, Object>> getLinkTypes(@Argument String sourceObjectType) {
        var allLinks = linkTypeRepository.findAll();
        return allLinks.stream()
                .filter(lt -> {
                    if (sourceObjectType == null) return true;
                    var sourceType = objectTypeRepository.findById(lt.getSourceObjectTypeId());
                    return sourceType.isPresent() && sourceType.get().getApiName().equals(sourceObjectType);
                })
                .map(lt -> {
                    String targetName = objectTypeRepository.findById(lt.getTargetObjectTypeId())
                            .map(t -> t.getApiName()).orElse("unknown");
                    return Map.<String, Object>of(
                            "apiName", lt.getApiName(),
                            "displayName", lt.getDisplayName(),
                            "targetObjectType", targetName,
                            "cardinality", lt.getCardinality()
                    );
                })
                .toList();
    }

    @QueryMapping
    public List<Map<String, Object>> getActionLog(@Argument String objectId, @Argument Integer limit) {
        UUID objId = objectId != null ? UUID.fromString(objectId) : null;
        int lim = limit != null ? limit : 50;
        return auditService.getActionLog(objId, lim).stream()
                .map(log -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", log.getId().toString());
                    map.put("actionType", log.getActionTypeName() != null ? log.getActionTypeName() : log.getActionTypeId().toString());
                    map.put("objectId", log.getObjectId() != null ? log.getObjectId().toString() : null);
                    map.put("performedBy", log.getPerformedBy());
                    map.put("status", log.getStatus());
                    map.put("beforeState", log.getBeforeState());
                    map.put("afterState", log.getAfterState());
                    map.put("performedAt", log.getPerformedAt());
                    return map;
                })
                .toList();
    }

    @QueryMapping
    public List<Map<String, Object>> getActionTypes(@Argument String objectType) {
        UUID typeId = null;
        if (objectType != null) {
            typeId = objectTypeRepository.findByIsActiveTrue().stream()
                    .filter(ot -> ot.getApiName().equals(objectType))
                    .findFirst()
                    .map(ot -> ot.getId())
                    .orElse(null);
        }
        return actionEngine.getActionTypes(typeId).stream()
                .map(at -> {
                    String targetName = at.getTargetObjectTypeId() != null
                            ? objectTypeRepository.findById(at.getTargetObjectTypeId())
                                .map(t -> t.getApiName()).orElse("unknown")
                            : "unknown";
                    return Map.<String, Object>of(
                            "id", at.getId().toString(),
                            "apiName", at.getApiName(),
                            "displayName", at.getDisplayName(),
                            "targetObjectType", targetName,
                            "requiresApproval", at.isRequiresApproval(),
                            "validationRules", at.getValidationRules()
                    );
                })
                .toList();
    }

    @SchemaMapping(typeName = "OntologyObject", field = "linkedObjects")
    public List<Map<String, Object>> linkedObjects(Map<String, Object> source,
                                                     @Argument String linkType) {
        UUID objectId = UUID.fromString(source.get("id").toString());
        return objectService.getLinkedObjects(objectId, linkType)
                .stream().map(this::objectToMap).toList();
    }

    private Map<String, Object> objectToMap(OntologyObject obj) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", obj.getId().toString());
        map.put("objectType", obj.getObjectTypeName());
        map.put("properties", obj.getProperties());
        map.put("createdAt", obj.getCreatedAt());
        map.put("updatedAt", obj.getUpdatedAt());
        return map;
    }

    private Map<String, Object> objectTypeSchemaToMap(com.nexoai.ontology.adapters.out.persistence.entity.ObjectTypeEntity entity) {
        var domain = objectTypeMapper.toDomain(entity);
        List<Map<String, Object>> props = domain.getProperties().stream()
                .map(p -> Map.<String, Object>of(
                        "apiName", p.getApiName(),
                        "displayName", p.getDisplayName(),
                        "dataType", p.getDataType().name(),
                        "isPrimaryKey", p.isPrimaryKey(),
                        "isRequired", p.isRequired()
                ))
                .toList();

        var linkEntities = linkTypeRepository.findAll().stream()
                .filter(lt -> lt.getSourceObjectTypeId().equals(entity.getId()))
                .toList();
        List<Map<String, Object>> links = linkEntities.stream()
                .map(lt -> {
                    String targetName = objectTypeRepository.findById(lt.getTargetObjectTypeId())
                            .map(t -> t.getApiName()).orElse("unknown");
                    return Map.<String, Object>of(
                            "apiName", lt.getApiName(),
                            "displayName", lt.getDisplayName(),
                            "targetObjectType", targetName,
                            "cardinality", lt.getCardinality()
                    );
                })
                .toList();

        Map<String, Object> map = new HashMap<>();
        map.put("id", domain.getId().toString());
        map.put("apiName", domain.getApiName());
        map.put("displayName", domain.getDisplayName());
        map.put("description", domain.getDescription());
        map.put("properties", props);
        map.put("linkTypes", links);
        return map;
    }
}
