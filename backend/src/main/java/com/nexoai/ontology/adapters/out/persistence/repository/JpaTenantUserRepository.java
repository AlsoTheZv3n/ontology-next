package com.nexoai.ontology.adapters.out.persistence.repository;

import com.nexoai.ontology.adapters.out.persistence.entity.TenantUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaTenantUserRepository extends JpaRepository<TenantUserEntity, UUID> {
    Optional<TenantUserEntity> findByEmail(String email);
    List<TenantUserEntity> findByTenantId(UUID tenantId);
    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
