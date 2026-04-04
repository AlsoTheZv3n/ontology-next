package com.nexoai.ontology.core.notification;

import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JdbcTemplate jdbcTemplate;

    public UUID notify(UUID tenantId, String userEmail, String type, String title,
                       String message, String metadata) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, tenant_id, user_email, type, title, message, metadata)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?::jsonb)
                """,
                id.toString(), tenantId.toString(), userEmail, type, title, message,
                metadata != null ? metadata : "{}");
        log.debug("Notification created for user {} in tenant {}: {}", userEmail, tenantId, title);
        return id;
    }

    public List<Map<String, Object>> getUnread(UUID tenantId, String userEmail) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM notifications
                WHERE tenant_id = ?::uuid AND user_email = ? AND is_read = FALSE
                ORDER BY created_at DESC
                LIMIT 100
                """,
                tenantId.toString(), userEmail);
    }

    public List<Map<String, Object>> getAll(UUID tenantId, String userEmail, int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM notifications
                WHERE tenant_id = ?::uuid AND user_email = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                tenantId.toString(), userEmail, Math.min(limit, 200));
    }

    public void markRead(UUID notificationId) {
        int updated = jdbcTemplate.update(
                "UPDATE notifications SET is_read = TRUE WHERE id = ?::uuid",
                notificationId.toString());
        if (updated == 0) {
            throw new OntologyException("Notification not found: " + notificationId);
        }
    }

    public void markAllRead(UUID tenantId, String userEmail) {
        jdbcTemplate.update(
                """
                UPDATE notifications SET is_read = TRUE
                WHERE tenant_id = ?::uuid AND user_email = ? AND is_read = FALSE
                """,
                tenantId.toString(), userEmail);
    }

    public List<Map<String, Object>> getPreferences(UUID tenantId, String userEmail) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM notification_preferences
                WHERE tenant_id = ?::uuid AND user_email = ?
                """,
                tenantId.toString(), userEmail);
    }

    public void upsertPreference(UUID tenantId, String userEmail, String channel,
                                  String eventTypes, String config) {
        jdbcTemplate.update(
                """
                INSERT INTO notification_preferences (tenant_id, user_email, channel, event_types, config)
                VALUES (?::uuid, ?, ?, ?::jsonb, ?::jsonb)
                ON CONFLICT (tenant_id, user_email, channel)
                DO UPDATE SET event_types = EXCLUDED.event_types, config = EXCLUDED.config
                """,
                tenantId.toString(), userEmail, channel != null ? channel : "IN_APP",
                eventTypes != null ? eventTypes : "[]",
                config != null ? config : "{}");
    }
}
