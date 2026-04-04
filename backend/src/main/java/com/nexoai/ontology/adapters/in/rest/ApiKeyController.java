package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.apikey.ApiKeyService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createKey(@RequestBody Map<String, Object> request) {
        UUID tenantId = TenantContext.getTenantId();
        String name = (String) request.get("name");
        String scopes = request.get("scopes") != null ? request.get("scopes").toString() : null;
        OffsetDateTime expiresAt = request.get("expiresAt") != null
                ? OffsetDateTime.parse((String) request.get("expiresAt"))
                : null;

        Map<String, Object> result = apiKeyService.createKey(tenantId, name, scopes, expiresAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listKeys() {
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(apiKeyService.listKeys(tenantId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(@PathVariable UUID id) {
        apiKeyService.revokeKey(id);
        return ResponseEntity.noContent().build();
    }
}
