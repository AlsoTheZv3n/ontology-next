package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.PendingApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaPendingApprovalRepository extends JpaRepository<PendingApprovalEntity, UUID> {
    List<PendingApprovalEntity> findBySessionIdAndStatus(UUID sessionId, String status);
}
