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
import com.nexoai.ontology.core.entityresolution.ResolutionRunner;
import com.nexoai.ontology.core.exception.OntologyException;
import com.nexoai.ontology.core.lineage.LineageService;
import com.nexoai.ontology.core.tenant.TenantContext;
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
    private final ResolutionRunner resolutionRunner;
    private final LineageService lineageService;

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
        OntologyObject domain = toDomain(saved, objectTypeName);
        triggerResolutionCheck(domain);
        return domain;
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
        return updateObjectProperties(objectId, newProperties,
                LineageService.SourceType.USER, null, "api");
    }

    /**
     * Patch-merge object properties and record per-field lineage. Sources other than USER
     * (CONNECTOR, CDC, ACTION, AGENT) call this overload so the lineage entry carries the
     * right provenance.
     */
    public OntologyObject updateObjectProperties(UUID objectId, JsonNode newProperties,
                                                  LineageService.SourceType sourceType,
                                                  String sourceId, String sourceName) {
        var entity = objectRepository.findById(objectId)
                .orElseThrow(() -> new OntologyException("Object not found: " + objectId));

        JsonNode existing = parseJson(entity.getProperties());
        ObjectNode merged = existing.deepCopy().isObject()
                ? (ObjectNode) existing.deepCopy()
                : objectMapper.createObjectNode();
        newProperties.fields().forEachRemaining(f -> merged.set(f.getKey(), f.getValue()));

        entity.setProperties(merged.toString());
        var saved = objectRepository.save(entity);
        String typeName = resolveObjectTypeName(saved.getObjectTypeId());
        OntologyObject domain = toDomain(saved, typeName);

        recordLineageSafely(objectId, existing, newProperties, sourceType, sourceId, sourceName);
        triggerResolutionCheck(domain);
        return domain;
    }

    private void recordLineageSafely(UUID objectId, JsonNode oldProps, JsonNode patch,
                                      LineageService.SourceType sourceType,
                                      String sourceId, String sourceName) {
        try {
            lineageService.recordDiff(objectId, oldProps, patch, sourceType, sourceId, sourceName,
                    TenantContext.getCurrentUser());
        } catch (Exception e) {
            // Lineage is observability, not a correctness-critical path.
            // An upsert must never fail because the lineage table is unavailable.
        }
    }

    /**
     * Fire-and-forget duplicate detection. Captures TenantContext on the calling thread and
     * passes the tenantId explicitly because @Async runs on a pool that doesn't inherit
     * ThreadLocals. Any failure is swallowed — resolution is best-effort and must never
     * fail an upsert.
     */
    private void triggerResolutionCheck(OntologyObject subject) {
        try {
            UUID tenantId = TenantContext.getTenantIdOrNull();
            if (tenantId == null) return;
            resolutionRunner.scheduleCheck(subject, tenantId);
        } catch (Exception ignored) {
            // never fail the upsert because of a side-channel
        }
    }

    @Transactional(readOnly = true)
    public Optional<OntologyObject> findByExternalId(String externalId) {
        // Search across all data sources for any object with this externalId
        return objectRepository.findAll().stream()
                .filter(o -> externalId.equals(o.getExternalId()))
                .findFirst()
                .map(e -> toDomain(e, resolveObjectTypeName(e.getObjectTypeId())));
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
