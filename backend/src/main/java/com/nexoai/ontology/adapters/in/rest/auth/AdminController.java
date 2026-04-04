package com.nexoai.ontology.adapters.in.rest.auth;

import com.nexoai.ontology.adapters.out.persistence.entity.TenantEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.TenantUserEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaTenantRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaTenantUserRepository;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final JpaTenantRepository tenantRepository;
    private final JpaTenantUserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/tenants")
    public ResponseEntity<List<TenantEntity>> getAllTenants() {
        return ResponseEntity.ok(tenantRepository.findAll());
    }

    @PostMapping("/tenants")
    public ResponseEntity<TenantEntity> createTenant(@RequestBody Map<String, Object> request) {
        String apiName = (String) request.get("apiName");
        String displayName = (String) request.get("displayName");
        String plan = request.getOrDefault("plan", "FREE").toString();

        if (tenantRepository.existsByApiName(apiName)) {
            throw new OntologyException("Tenant already exists: " + apiName);
        }

        TenantEntity tenant = tenantRepository.save(TenantEntity.builder()
                .apiName(apiName)
                .displayName(displayName)
                .plan(plan)
                .build());

        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    @GetMapping("/tenants/{id}/stats")
    public ResponseEntity<Map<String, Object>> getTenantStats(@PathVariable UUID id) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new OntologyException("Tenant not found"));

        String tid = id.toString();
        Long objectTypes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM object_types WHERE tenant_id = ?::uuid", Long.class, tid);
        Long objects = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology_objects WHERE tenant_id = ?::uuid", Long.class, tid);
        Long connectors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_source_definitions WHERE tenant_id = ?::uuid", Long.class, tid);
        Long actions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM action_log WHERE tenant_id = ?::uuid", Long.class, tid);
        Long users = (long) userRepository.findByTenantId(id).size();

        return ResponseEntity.ok(Map.of(
                "tenant", tenant,
                "stats", Map.of(
                        "objectTypes", Map.of("current", objectTypes, "max", tenant.getMaxObjectTypes()),
                        "objects", Map.of("current", objects, "max", tenant.getMaxObjects()),
                        "connectors", Map.of("current", connectors, "max", tenant.getMaxConnectors()),
                        "actions", actions,
                        "users", users
                )
        ));
    }

    @PutMapping("/tenants/{id}/plan")
    public ResponseEntity<TenantEntity> updatePlan(@PathVariable UUID id, @RequestBody Map<String, Object> request) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new OntologyException("Tenant not found"));

        String plan = (String) request.get("plan");
        tenant.setPlan(plan);

        // Update limits based on plan
        switch (plan) {
            case "FREE" -> { tenant.setMaxObjectTypes(10); tenant.setMaxObjects(10000); tenant.setMaxConnectors(3); }
            case "STARTER" -> { tenant.setMaxObjectTypes(50); tenant.setMaxObjects(100000); tenant.setMaxConnectors(10); }
            case "PRO" -> { tenant.setMaxObjectTypes(200); tenant.setMaxObjects(1000000); tenant.setMaxConnectors(50); }
            case "ENTERPRISE" -> { tenant.setMaxObjectTypes(1000); tenant.setMaxObjects(10000000); tenant.setMaxConnectors(500); }
        }

        return ResponseEntity.ok(tenantRepository.save(tenant));
    }

    @DeleteMapping("/tenants/{id}")
    public ResponseEntity<Void> deactivateTenant(@PathVariable UUID id) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new OntologyException("Tenant not found"));
        tenant.setActive(false);
        tenantRepository.save(tenant);
        return ResponseEntity.noContent().build();
    }
}
