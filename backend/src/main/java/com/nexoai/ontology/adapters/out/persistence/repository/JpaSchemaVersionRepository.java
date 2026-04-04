package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.SchemaVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSchemaVersionRepository extends JpaRepository<SchemaVersionEntity, UUID> {
    List<SchemaVersionEntity> findByObjectTypeIdOrderByVersionDesc(UUID objectTypeId);
    Optional<SchemaVersionEntity> findByObjectTypeIdAndVersion(UUID objectTypeId, int version);
    Optional<SchemaVersionEntity> findTopByObjectTypeIdOrderByVersionDesc(UUID objectTypeId);
}
