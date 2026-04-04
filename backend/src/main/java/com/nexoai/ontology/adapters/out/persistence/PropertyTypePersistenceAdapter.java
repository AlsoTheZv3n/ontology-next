package com.nexoai.ontology.adapters.out.persistence;

import com.nexoai.ontology.adapters.out.persistence.mapper.ObjectTypeMapper;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaPropertyTypeRepository;
import com.nexoai.ontology.core.domain.PropertyType;
import com.nexoai.ontology.core.domain.ports.out.PropertyTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PropertyTypePersistenceAdapter implements PropertyTypeRepository {

    private final JpaPropertyTypeRepository jpaRepository;
    private final JpaObjectTypeRepository objectTypeJpaRepository;
    private final ObjectTypeMapper mapper;

    @Override
    public PropertyType save(PropertyType propertyType, UUID objectTypeId) {
        var objectTypeEntity = objectTypeJpaRepository.findById(objectTypeId)
                .orElseThrow(() -> new IllegalArgumentException("ObjectType not found: " + objectTypeId));
        var entity = mapper.toPropertyEntity(propertyType);
        entity.setObjectType(objectTypeEntity);
        var saved = jpaRepository.save(entity);
        return mapper.toPropertyDomain(saved);
    }

    @Override
    public List<PropertyType> findByObjectTypeId(UUID objectTypeId) {
        return jpaRepository.findByObjectTypeId(objectTypeId).stream()
                .map(mapper::toPropertyDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
