package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaOntologyObjectRepository extends JpaRepository<OntologyObjectEntity, UUID> {
    Page<OntologyObjectEntity> findByObjectTypeId(UUID objectTypeId, Pageable pageable);

    @Query("SELECT o FROM OntologyObjectEntity o WHERE o.objectTypeId = :typeId AND CAST(o.properties AS string) LIKE %:value%")
    Page<OntologyObjectEntity> searchByObjectTypeAndPropertyContaining(
            @Param("typeId") UUID objectTypeId,
            @Param("value") String value,
            Pageable pageable);

    long countByObjectTypeId(UUID objectTypeId);

    Optional<OntologyObjectEntity> findByExternalIdAndDataSourceId(String externalId, UUID dataSourceId);
}
