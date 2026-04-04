package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.exception.OntologyException;
import com.nexoai.ontology.core.export.TenantExportService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data/export")
@RequiredArgsConstructor
public class DataExportController {

    private final TenantExportService tenantExportService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> startExport(
            @RequestBody(required = false) Map<String, String> request) {
        UUID tenantId = TenantContext.getTenantId();
        String scope = request != null ? request.getOrDefault("scope", "FULL") : "FULL";
        UUID jobId = tenantExportService.startExport(tenantId, scope);
        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "status", "PENDING",
                "message", "Export job started"));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(tenantExportService.getExportStatus(jobId));
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId) {
        String filePath = tenantExportService.getExportFilePath(jobId);
        File file = new File(filePath);
        if (!file.exists()) {
            throw new OntologyException("Export file not found on disk");
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export-" + jobId + ".json\"")
                .body(resource);
    }
}
