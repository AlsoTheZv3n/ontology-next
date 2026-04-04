package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.SyncResultLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaSyncResultLogRepository extends JpaRepository<SyncResultLogEntity, UUID> {
    List<SyncResultLogEntity> findByDataSourceIdOrderByStartedAtDesc(UUID dataSourceId, Pageable pageable);
}
