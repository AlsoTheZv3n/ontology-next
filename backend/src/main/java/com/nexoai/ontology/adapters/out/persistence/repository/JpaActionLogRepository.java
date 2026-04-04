package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.ActionLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaActionLogRepository extends JpaRepository<ActionLogEntity, UUID> {
    List<ActionLogEntity> findByObjectIdOrderByPerformedAtDesc(UUID objectId, Pageable pageable);
    List<ActionLogEntity> findAllByOrderByPerformedAtDesc(Pageable pageable);
}
