package com.nexoai.ontology.core.domain.ports.in;

import com.nexoai.ontology.core.domain.PropertyType;

import java.util.UUID;

public interface ManagePropertyTypeUseCase {
    PropertyType addProperty(UUID objectTypeId, RegisterObjectTypeUseCase.PropertyTypeCommand command);
    void removeProperty(UUID objectTypeId, UUID propertyId);
}
