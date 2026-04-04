package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.notification.NotificationService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getNotifications(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        UUID tenantId = TenantContext.getTenantId();
        String userEmail = TenantContext.getCurrentUser();
        List<Map<String, Object>> notifications = unreadOnly
                ? notificationService.getUnread(tenantId, userEmail)
                : notificationService.getAll(tenantId, userEmail, limit);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        UUID tenantId = TenantContext.getTenantId();
        String userEmail = TenantContext.getCurrentUser();
        notificationService.markAllRead(tenantId, userEmail);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<List<Map<String, Object>>> getPreferences() {
        UUID tenantId = TenantContext.getTenantId();
        String userEmail = TenantContext.getCurrentUser();
        return ResponseEntity.ok(notificationService.getPreferences(tenantId, userEmail));
    }

    @PutMapping("/preferences")
    public ResponseEntity<Void> updatePreferences(@RequestBody Map<String, String> request) {
        UUID tenantId = TenantContext.getTenantId();
        String userEmail = TenantContext.getCurrentUser();
        notificationService.upsertPreference(
                tenantId, userEmail,
                request.get("channel"),
                request.get("eventTypes"),
                request.get("config"));
        return ResponseEntity.ok().build();
    }
}
