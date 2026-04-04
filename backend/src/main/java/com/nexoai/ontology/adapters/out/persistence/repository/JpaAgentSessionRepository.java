package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.AgentSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaAgentSessionRepository extends JpaRepository<AgentSessionEntity, UUID> {
    List<AgentSessionEntity> findByTenantIdOrderByLastActiveAtDesc(UUID tenantId);
}
