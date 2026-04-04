package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.SchemaMigrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaSchemaMigrationRepository extends JpaRepository<SchemaMigrationEntity, UUID> {
    List<SchemaMigrationEntity> findByObjectTypeIdAndFromVersionAndToVersion(
            UUID objectTypeId, int fromVersion, int toVersion);
}
