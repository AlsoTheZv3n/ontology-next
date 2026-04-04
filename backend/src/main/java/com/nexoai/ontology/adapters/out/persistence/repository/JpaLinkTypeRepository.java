package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.LinkTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaLinkTypeRepository extends JpaRepository<LinkTypeEntity, UUID> {
    boolean existsByApiName(String apiName);
}
