package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.reporting.ReportingService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    @PostMapping("/api/v1/reporting/aggregate")
    public ResponseEntity<Map<String, Object>> aggregate(@RequestBody Map<String, Object> request) {
        UUID tenantId = TenantContext.getTenantId();
        String objectType = (String) request.get("objectType");
        String operation = (String) request.get("operation");
        String property = (String) request.get("property");

        Map<String, Object> result = reportingService.aggregate(tenantId, objectType, operation, property);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/v1/dashboards")
    public ResponseEntity<List<Map<String, Object>>> listDashboards() {
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(reportingService.getDashboards(tenantId));
    }

    @PostMapping("/api/v1/dashboards")
    public ResponseEntity<Map<String, Object>> saveDashboard(@RequestBody Map<String, Object> request) {
        UUID tenantId = TenantContext.getTenantId();
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        String widgets = request.get("widgets") != null ? request.get("widgets").toString() : null;
        String layout = request.get("layout") != null ? request.get("layout").toString() : null;

        Map<String, Object> dashboard = reportingService.saveDashboard(tenantId, name, description, widgets, layout);
        return ResponseEntity.status(HttpStatus.CREATED).body(dashboard);
    }

    @GetMapping("/api/v1/dashboards/{id}")
    public ResponseEntity<Map<String, Object>> getDashboard(@PathVariable UUID id) {
        return ResponseEntity.ok(reportingService.getDashboard(id));
    }
}
