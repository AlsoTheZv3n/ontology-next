package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.ActionTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaActionTypeRepository extends JpaRepository<ActionTypeEntity, UUID> {
    Optional<ActionTypeEntity> findByApiName(String apiName);
    boolean existsByApiName(String apiName);
    List<ActionTypeEntity> findByTargetObjectTypeId(UUID targetObjectTypeId);
}
