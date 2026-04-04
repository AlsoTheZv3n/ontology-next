package com.nexoai.ontology.core.domain.ports.out;

import com.nexoai.ontology.core.domain.PropertyType;

import java.util.List;
import java.util.UUID;

public interface PropertyTypeRepository {
    PropertyType save(PropertyType propertyType, UUID objectTypeId);
    List<PropertyType> findByObjectTypeId(UUID objectTypeId);
    void deleteById(UUID id);
}
