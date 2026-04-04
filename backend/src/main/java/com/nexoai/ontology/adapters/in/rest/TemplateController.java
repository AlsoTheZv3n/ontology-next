package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.template.TemplateService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listTemplates(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(templateService.listTemplates(category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PostMapping("/{id}/import")
    public ResponseEntity<Map<String, Object>> importTemplate(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> result = templateService.importTemplate(tenantId, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
