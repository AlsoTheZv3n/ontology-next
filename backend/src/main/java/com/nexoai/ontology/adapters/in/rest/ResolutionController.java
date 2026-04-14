package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.entityresolution.ResolutionService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for the entity-resolution review workflow.
 *
 * GET  /api/v1/resolution/pending          -> list pending duplicate candidates
 * POST /api/v1/resolution/{id}/approve     -> confirm that A and B are duplicates
 * POST /api/v1/resolution/{id}/reject      -> mark as not duplicates
 */
@RestController
@RequestMapping("/api/v1/resolution")
@RequiredArgsConstructor
public class ResolutionController {

    private final ResolutionService resolutionService;

    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> pending(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(resolutionService.pending(Math.min(limit, 200)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(resolutionService.approve(id, TenantContext.getCurrentUser()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(resolutionService.reject(id, TenantContext.getCurrentUser()));
    }
}
