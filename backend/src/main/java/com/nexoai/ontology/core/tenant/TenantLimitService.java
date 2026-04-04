package com.nexoai.ontology.core.tenant;

import com.nexoai.ontology.adapters.out.persistence.entity.TenantEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaTenantRepository;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantLimitService {

    private final JpaTenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;

    public void checkObjectTypeLimit(UUID tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new OntologyException("Tenant not found"));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM object_types WHERE tenant_id = ?::uuid", Long.class, tenantId.toString());
        if (count != null && count >= tenant.getMaxObjectTypes()) {
            throw new OntologyException("Object Type limit reached (%d/%d). Upgrade your plan."
                    .formatted(count, tenant.getMaxObjectTypes()));
        }
    }

    public void checkObjectLimit(UUID tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new OntologyException("Tenant not found"));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology_objects WHERE tenant_id = ?::uuid", Long.class, tenantId.toString());
        if (count != null && count >= tenant.getMaxObjects()) {
            throw new OntologyException("Object limit reached (%d/%d). Upgrade your plan."
                    .formatted(count, tenant.getMaxObjects()));
        }
    }

    public void checkConnectorLimit(UUID tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new OntologyException("Tenant not found"));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_source_definitions WHERE tenant_id = ?::uuid", Long.class, tenantId.toString());
        if (count != null && count >= tenant.getMaxConnectors()) {
            throw new OntologyException("Connector limit reached (%d/%d). Upgrade your plan."
                    .formatted(count, tenant.getMaxConnectors()));
        }
    }
}
