package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.collab.CollaborationService;
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
public class CollaborationController {

    private final CollaborationService collaborationService;

    // --- Comments ---

    @PostMapping("/api/v1/objects/{objectId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable UUID objectId,
                                                           @RequestBody Map<String, Object> request) {
        UUID tenantId = TenantContext.getTenantId();
        String author = request.get("author") != null
                ? (String) request.get("author")
                : TenantContext.getCurrentUser();
        String content = (String) request.get("content");

        Map<String, Object> comment = collaborationService.addComment(tenantId, objectId, author, content);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @GetMapping("/api/v1/objects/{objectId}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable UUID objectId) {
        return ResponseEntity.ok(collaborationService.getComments(objectId));
    }

    // --- Watches ---

    @PostMapping("/api/v1/objects/{objectId}/watch")
    public ResponseEntity<Map<String, Object>> toggleWatch(@PathVariable UUID objectId,
                                                            @RequestBody(required = false) Map<String, Object> request) {
        UUID tenantId = TenantContext.getTenantId();
        String email = (request != null && request.get("email") != null)
                ? (String) request.get("email")
                : TenantContext.getCurrentUser();

        Map<String, Object> result = collaborationService.watch(tenantId, objectId, email);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/v1/objects/{objectId}/watchers")
    public ResponseEntity<List<Map<String, Object>>> getWatchers(@PathVariable UUID objectId) {
        return ResponseEntity.ok(collaborationService.getWatchers(objectId));
    }

    // --- Saved Searches ---

    @PostMapping("/api/v1/saved-searches")
    public ResponseEntity<Map<String, Object>> saveSearch(@RequestBody Map<String, Object> request) {
        UUID tenantId = TenantContext.getTenantId();
        String email = request.get("email") != null
                ? (String) request.get("email")
                : TenantContext.getCurrentUser();
        String name = (String) request.get("name");
        String query = request.get("query") != null ? request.get("query").toString() : null;
        boolean isShared = Boolean.TRUE.equals(request.get("isShared"));

        Map<String, Object> search = collaborationService.saveSearch(tenantId, email, name, query, isShared);
        return ResponseEntity.status(HttpStatus.CREATED).body(search);
    }

    @GetMapping("/api/v1/saved-searches")
    public ResponseEntity<List<Map<String, Object>>> getSavedSearches(
            @RequestParam(required = false) String email) {
        UUID tenantId = TenantContext.getTenantId();
        String userEmail = email != null ? email : TenantContext.getCurrentUser();
        return ResponseEntity.ok(collaborationService.getSavedSearches(tenantId, userEmail));
    }
}
