package com.nexoai.ontology.core.service.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.adapters.out.persistence.entity.ObjectLinkEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectLinkRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaLinkTypeRepository;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class OntologyObjectService {

    private final JpaOntologyObjectRepository objectRepository;
    private final JpaObjectTypeRepository objectTypeRepository;
    private final JpaObjectLinkRepository linkRepository;
    private final JpaLinkTypeRepository linkTypeRepository;
    private final ObjectMapper objectMapper;

    public OntologyObject createObject(String objectTypeName, JsonNode properties) {
        var objectType = objectTypeRepository.findByIsActiveTrue().stream()
                .filter(ot -> ot.getApiName().equals(objectTypeName))
                .findFirst()
                .orElseThrow(() -> new OntologyException("ObjectType not found: " + objectTypeName));

        var now = java.time.Instant.now();
        OntologyObjectEntity entity = OntologyObjectEntity.builder()
                .objectTypeId(objectType.getId())
                .properties(properties.toString())
                .createdAt(now)
                .updatedAt(now)
                .build();
        var saved = objectRepository.save(entity);
        return toDomain(saved, objectTypeName);
    }

    @Transactional(readOnly = true)
    public OntologyObject getObject(UUID id) {
        var entity = objectRepository.findById(id)
                .orElseThrow(() -> new OntologyException("Object not found: " + id));
        String typeName = resolveObjectTypeName(entity.getObjectTypeId());
        return toDomain(entity, typeName);
    }

    @Transactional(readOnly = true)
    public ObjectPage searchObjects(String objectTypeName, int limit, String cursor) {
        var objectType = objectTypeRepository.findByIsActiveTrue().stream()
                .filter(ot -> ot.getApiName().equals(objectTypeName))
                .findFirst()
                .orElseThrow(() -> new OntologyException("ObjectType not found: " + objectTypeName));

        Page<OntologyObjectEntity> page = objectRepository.findByObjectTypeId(
                objectType.getId(), PageRequest.of(0, limit));

        List<OntologyObject> items = page.getContent().stream()
                .map(e -> toDomain(e, objectTypeName))
                .toList();

        long totalCount = page.getTotalElements();
        return new ObjectPage(items, totalCount, page.hasNext(), null);
    }

    public OntologyObject updateObjectProperties(UUID objectId, JsonNode newProperties) {
        var entity = objectRepository.findById(objectId)
                .orElseThrow(() -> new OntologyException("Object not found: " + objectId));

        // Merge: existing properties + new properties
        JsonNode existing = parseJson(entity.getProperties());
        ObjectNode merged = existing.deepCopy().isObject()
                ? (ObjectNode) existing.deepCopy()
                : objectMapper.createObjectNode();
        newProperties.fields().forEachRemaining(f -> merged.set(f.getKey(), f.getValue()));

        entity.setProperties(merged.toString());
        var saved = objectRepository.save(entity);
        String typeName = resolveObjectTypeName(saved.getObjectTypeId());
        return toDomain(saved, typeName);
    }

    public boolean deleteObject(String objectTypeName, UUID objectId) {
        if (!objectRepository.existsById(objectId)) {
            throw new OntologyException("Object not found: " + objectId);
        }
        objectRepository.deleteById(objectId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<OntologyObject> getLinkedObjects(UUID objectId, String linkTypeName) {
        var linkType = linkTypeRepository.findAll().stream()
                .filter(lt -> lt.getApiName().equals(linkTypeName))
                .findFirst()
                .orElseThrow(() -> new OntologyException("LinkType not found: " + linkTypeName));

        List<ObjectLinkEntity> links = linkRepository.findBySourceIdAndLinkTypeId(objectId, linkType.getId());
        return links.stream()
                .map(link -> {
                    var entity = objectRepository.findById(link.getTargetId()).orElse(null);
                    if (entity == null) return null;
                    String typeName = resolveObjectTypeName(entity.getObjectTypeId());
                    return toDomain(entity, typeName);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OntologyObject> traverseLinks(UUID objectId, String linkTypeName, int depth) {
        if (depth <= 0) depth = 1;
        List<OntologyObject> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(objectId);
        traverseRecursive(objectId, linkTypeName, depth, result, visited);
        return result;
    }

    private void traverseRecursive(UUID objectId, String linkTypeName, int depth,
                                    List<OntologyObject> result, Set<UUID> visited) {
        if (depth <= 0) return;
        List<OntologyObject> linked = getLinkedObjects(objectId, linkTypeName);
        for (OntologyObject obj : linked) {
            if (!visited.contains(obj.getId())) {
                visited.add(obj.getId());
                result.add(obj);
                traverseRecursive(obj.getId(), linkTypeName, depth - 1, result, visited);
            }
        }
    }

    private String resolveObjectTypeName(UUID objectTypeId) {
        return objectTypeRepository.findById(objectTypeId)
                .map(ot -> ot.getApiName())
                .orElse("unknown");
    }

    private OntologyObject toDomain(OntologyObjectEntity entity, String objectTypeName) {
        return OntologyObject.builder()
                .id(entity.getId())
                .objectTypeId(entity.getObjectTypeId())
                .objectTypeName(objectTypeName)
                .properties(parseJson(entity.getProperties()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private JsonNode parseJson(String json) {
        if (json == null) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    public record ObjectPage(List<OntologyObject> items, long totalCount, boolean hasNextPage, String cursor) {}
}
