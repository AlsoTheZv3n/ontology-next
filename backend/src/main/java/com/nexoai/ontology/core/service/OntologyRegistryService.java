package com.nexoai.ontology.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.domain.*;
import com.nexoai.ontology.core.domain.ports.in.*;
import com.nexoai.ontology.core.domain.ports.out.*;
import com.nexoai.ontology.core.exception.*;
import com.nexoai.ontology.core.tenant.TenantContext;
import com.nexoai.ontology.core.versioning.SchemaVersioningService;
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
public class OntologyRegistryService implements
        RegisterObjectTypeUseCase,
        UpdateObjectTypeUseCase,
        QueryObjectTypeUseCase,
        ManagePropertyTypeUseCase,
        ManageLinkTypeUseCase {

    private final ObjectTypeRepository objectTypeRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final LinkTypeRepository linkTypeRepository;
    private final SchemaVersioningService schemaVersioningService;
    private final ObjectMapper objectMapper;

    // ── ObjectType Registration ──────────────────────────────────────────

    @Override
    public ObjectType registerObjectType(RegisterObjectTypeCommand command) {
        if (objectTypeRepository.existsByApiName(command.apiName())) {
            throw new DuplicateApiNameException(command.apiName());
        }

        ObjectType objectType = ObjectType.builder()
                .apiName(command.apiName())
                .displayName(command.displayName())
                .description(command.description())
                .icon(command.icon())
                .color(command.color())
                .isActive(true)
                .properties(new ArrayList<>())
                .build();

        if (command.properties() != null) {
            for (PropertyTypeCommand propCmd : command.properties()) {
                PropertyType property = mapPropertyCommand(propCmd);
                objectType.addProperty(property);
            }
        }

        return objectTypeRepository.save(objectType);
    }

    // ── ObjectType Update ────────────────────────────────────────────────

    @Override
    public ObjectType updateObjectType(UUID id, UpdateObjectTypeCommand command) {
        ObjectType objectType = findObjectTypeOrThrow(id);

        // Fix 07: Create a schema version snapshot before applying changes
        try {
            ObjectNode schemaSnapshot = objectMapper.createObjectNode();
            schemaSnapshot.put("apiName", objectType.getApiName());
            schemaSnapshot.put("displayName", objectType.getDisplayName());
            schemaSnapshot.put("description", objectType.getDescription() != null ? objectType.getDescription() : "");
            schemaSnapshot.set("properties", objectMapper.valueToTree(
                    objectType.getProperties().stream().map(p -> {
                        ObjectNode pn = objectMapper.createObjectNode();
                        pn.put("apiName", p.getApiName());
                        pn.put("dataType", p.getDataType().name());
                        pn.put("isRequired", p.isRequired());
                        return pn;
                    }).toList()));

            String changeSummary = "Updated: displayName=%s, description=%s".formatted(
                    command.displayName(), command.description());
            schemaVersioningService.createVersion(id, schemaSnapshot, changeSummary, false,
                    TenantContext.getCurrentUser());
        } catch (Exception e) {
            log.warn("Could not create schema version for ObjectType {}: {}", id, e.getMessage());
        }

        objectType.setDisplayName(command.displayName());
        objectType.setDescription(command.description());
        objectType.setIcon(command.icon());
        objectType.setColor(command.color());
        return objectTypeRepository.save(objectType);
    }

    @Override
    public void deactivateObjectType(UUID id) {
        ObjectType objectType = findObjectTypeOrThrow(id);
        objectType.setActive(false);
        objectTypeRepository.save(objectType);
    }

    // ── ObjectType Query ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ObjectType getObjectType(UUID id) {
        return findObjectTypeOrThrow(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectType> getAllObjectTypes() {
        return objectTypeRepository.findAllActive();
    }

    // ── PropertyType Management ──────────────────────────────────────────

    @Override
    public PropertyType addProperty(UUID objectTypeId, RegisterObjectTypeUseCase.PropertyTypeCommand command) {
        ObjectType objectType = findObjectTypeOrThrow(objectTypeId);
        PropertyType property = mapPropertyCommand(command);
        objectType.addProperty(property);
        return propertyTypeRepository.save(property, objectTypeId);
    }

    @Override
    public void removeProperty(UUID objectTypeId, UUID propertyId) {
        findObjectTypeOrThrow(objectTypeId);
        propertyTypeRepository.deleteById(propertyId);
    }

    // ── LinkType Management ──────────────────────────────────────────────

    @Override
    public LinkType createLinkType(CreateLinkTypeCommand command) {
        if (linkTypeRepository.existsByApiName(command.apiName())) {
            throw new DuplicateApiNameException(command.apiName());
        }
        findObjectTypeOrThrow(command.sourceObjectTypeId());
        findObjectTypeOrThrow(command.targetObjectTypeId());

        LinkType linkType = LinkType.builder()
                .apiName(command.apiName())
                .displayName(command.displayName())
                .sourceObjectTypeId(command.sourceObjectTypeId())
                .targetObjectTypeId(command.targetObjectTypeId())
                .cardinality(command.cardinality() != null ? command.cardinality() : Cardinality.ONE_TO_MANY)
                .description(command.description())
                .build();

        return linkTypeRepository.save(linkType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkType> getAllLinkTypes() {
        return linkTypeRepository.findAll();
    }

    @Override
    public void deleteLinkType(UUID id) {
        linkTypeRepository.deleteById(id);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ObjectType findObjectTypeOrThrow(UUID id) {
        return objectTypeRepository.findById(id)
                .orElseThrow(() -> new ObjectTypeNotFoundException(id));
    }

    private PropertyType mapPropertyCommand(RegisterObjectTypeUseCase.PropertyTypeCommand cmd) {
        return PropertyType.builder()
                .apiName(cmd.apiName())
                .displayName(cmd.displayName())
                .dataType(cmd.dataType())
                .isPrimaryKey(cmd.isPrimaryKey())
                .isRequired(cmd.isRequired())
                .isIndexed(cmd.isIndexed())
                .defaultValue(cmd.defaultValue())
                .description(cmd.description())
                .build();
    }
}
