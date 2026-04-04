package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.quality.DataQualityService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quality")
@RequiredArgsConstructor
public class DataQualityController {

    private final DataQualityService dataQualityService;

    @GetMapping("/{objectType}/report")
    public ResponseEntity<Map<String, Object>> getLatestReport(@PathVariable UUID objectType) {
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(dataQualityService.getLatestReport(tenantId, objectType));
    }

    @PostMapping("/{objectType}/scan")
    public ResponseEntity<Map<String, Object>> triggerScan(@PathVariable UUID objectType) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> report = dataQualityService.runQualityReport(tenantId, objectType);
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    @GetMapping("/{objectType}/rules")
    public ResponseEntity<List<Map<String, Object>>> listRules(@PathVariable UUID objectType) {
        return ResponseEntity.ok(dataQualityService.getRules(objectType));
    }

    @PostMapping("/{objectType}/rules")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> addRule(@PathVariable UUID objectType,
                                                        @RequestBody Map<String, Object> request) {
        String propertyName = (String) request.get("propertyName");
        String ruleType = (String) request.get("ruleType");
        String ruleConfig = request.get("ruleConfig") != null ? request.get("ruleConfig").toString() : null;
        String errorMessage = (String) request.get("errorMessage");

        Map<String, Object> rule = dataQualityService.addRule(objectType, propertyName, ruleType,
                ruleConfig, errorMessage);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }
}
