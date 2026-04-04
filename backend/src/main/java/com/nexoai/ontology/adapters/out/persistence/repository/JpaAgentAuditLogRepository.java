package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.AgentAuditLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaAgentAuditLogRepository extends JpaRepository<AgentAuditLogEntity, UUID> {
    List<AgentAuditLogEntity> findByTenantIdOrderByPerformedAtDesc(UUID tenantId, Pageable pageable);
    List<AgentAuditLogEntity> findBySessionIdOrderByPerformedAtDesc(UUID sessionId);
}
