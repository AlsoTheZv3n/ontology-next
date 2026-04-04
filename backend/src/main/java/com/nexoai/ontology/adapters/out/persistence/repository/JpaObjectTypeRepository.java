package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.ObjectTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaObjectTypeRepository extends JpaRepository<ObjectTypeEntity, UUID> {
    boolean existsByApiName(String apiName);
    List<ObjectTypeEntity> findByIsActiveTrue();
}
