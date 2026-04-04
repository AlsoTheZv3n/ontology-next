package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.PropertyTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaPropertyTypeRepository extends JpaRepository<PropertyTypeEntity, UUID> {
    List<PropertyTypeEntity> findByObjectTypeId(UUID objectTypeId);
}
