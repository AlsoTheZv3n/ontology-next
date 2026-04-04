package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.billing.UsageMeteringService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BillingController {

    private final UsageMeteringService usageMeteringService;

    @GetMapping("/api/v1/billing/usage")
    public ResponseEntity<Map<String, Object>> getUsage() {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> summary = usageMeteringService.getUsageSummary(tenantId);
        return ResponseEntity.ok(Map.of("tenantId", tenantId, "usage", summary));
    }

    @GetMapping("/api/admin/billing/usage/{tenantId}")
    public ResponseEntity<Map<String, Object>> getUsageAdmin(@PathVariable UUID tenantId) {
        Map<String, Object> summary = usageMeteringService.getUsageSummary(tenantId);
        return ResponseEntity.ok(Map.of("tenantId", tenantId, "usage", summary));
    }
}
