package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.audit.AuditEventService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventService auditEventService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> query(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(auditEventService.query(tenantId, category, from, to, limit));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        UUID tenantId = TenantContext.getTenantId();
        String csv = auditEventService.exportCsv(tenantId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-export.csv\"")
                .body(csv);
    }

    @DeleteMapping("/gdpr/erase/{email}")
    public ResponseEntity<Map<String, Object>> gdprErase(@PathVariable String email) {
        UUID tenantId = TenantContext.getTenantId();
        int count = auditEventService.gdprErase(tenantId, email);
        return ResponseEntity.ok(Map.of(
                "email", email,
                "anonymizedRecords", count));
    }
}
