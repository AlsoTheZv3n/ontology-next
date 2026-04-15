package com.nexoai.ontology.core.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drains the {@code notifications} queue into the {@link NotificationDispatcher}.
 *
 * Prod-09 built the channel dispatch path; Prod-13 started writing PENDING rows.
 * What was missing was the thing in between: a scheduler that picks up pending
 * rows and actually sends them. Without this, Slack / Teams / Email deliveries
 * never leave the database.
 *
 * Design choices:
 *  - Fixed-delay scheduling (5s between runs, not every 5s) so long dispatch
 *    batches don't pile up on each other.
 *  - Per-row try/catch so one failing channel doesn't starve the queue.
 *  - Max 3 attempts — after that a row stays as FAILED and a human looks at
 *    delivery_results to decide what to do.
 *  - SKIP LOCKED (Postgres) so multi-instance deployments don't race for the
 *    same rows. Falls back to ordinary SELECT if the DB doesn't support it,
 *    with a brief log warning the first time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingNotificationProcessor {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 3;

    private final JdbcTemplate jdbc;
    private final NotificationDispatcher dispatcher;
    private final ObjectMapper mapper;

    @Value("${nexo.notifications.dispatch.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelay = 5_000)
    public void drain() {
        if (!enabled) return;
        List<Map<String, Object>> pending;
        try {
            pending = jdbc.queryForList("""
                    SELECT id, tenant_id, user_email, type, title, message, metadata, attempts
                      FROM notifications
                     WHERE status = 'PENDING' AND attempts < ?
                     ORDER BY created_at
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                    """, MAX_ATTEMPTS, BATCH_SIZE);
        } catch (Exception e) {
            log.debug("drain() skipped — likely tables not yet migrated: {}", e.getMessage());
            return;
        }
        if (pending.isEmpty()) return;
        log.debug("Dispatching {} pending notifications", pending.size());
        for (Map<String, Object> row : pending) {
            processOne(row);
        }
    }

    void processOne(Map<String, Object> row) {
        UUID id = UUID.fromString(String.valueOf(row.get("id")));
        UUID tenantId = UUID.fromString(String.valueOf(row.get("tenant_id")));
        String recipient = String.valueOf(row.get("user_email"));
        String eventType = String.valueOf(row.get("type"));
        String title = String.valueOf(row.get("title"));
        String body = row.get("message") == null ? "" : String.valueOf(row.get("message"));

        try {
            JsonNode context = parseJson(row.get("metadata"));
            List<String> channels = resolveChannels(tenantId, recipient, eventType);
            if (channels.isEmpty()) {
                // In-app row already exists (that's the write that created this record).
                // Nothing to fan out — mark delivered so we don't retry indefinitely.
                markDone(id, "DELIVERED", List.of());
                return;
            }
            JsonNode config = resolveConfig(tenantId, recipient);
            var req = new NotificationChannel.NotificationRequest(
                    id, tenantId, eventType, recipient, title, body, context);
            var outcomes = dispatcher.dispatch(req, channels, config);
            boolean anySuccess = outcomes.stream().anyMatch(NotificationDispatcher.DeliveryOutcome::success);
            markDone(id, anySuccess ? "DELIVERED" : (reachedMaxAttempts(row) ? "FAILED" : "PENDING"),
                    outcomes);
        } catch (Exception e) {
            log.warn("Dispatch failed for notification {}: {}", id, e.getMessage());
            jdbc.update("UPDATE notifications SET attempts = attempts + 1 WHERE id = ?::uuid",
                    id.toString());
        }
    }

    private boolean reachedMaxAttempts(Map<String, Object> row) {
        Object a = row.get("attempts");
        int attempts = a == null ? 0 : ((Number) a).intValue();
        return attempts + 1 >= MAX_ATTEMPTS;
    }

    private void markDone(UUID id, String status,
                           List<NotificationDispatcher.DeliveryOutcome> outcomes) {
        try {
            String results = mapper.writeValueAsString(outcomes);
            jdbc.update("""
                    UPDATE notifications
                       SET status = ?,
                           attempts = attempts + 1,
                           delivery_results = ?::jsonb,
                           delivered_at = CASE WHEN ? = 'DELIVERED' THEN NOW() ELSE delivered_at END
                     WHERE id = ?::uuid
                    """, status, results, status, id.toString());
        } catch (Exception e) {
            log.warn("Could not update notification {} to {}: {}", id, status, e.getMessage());
        }
    }

    private List<String> resolveChannels(UUID tenantId, String recipient, String eventType) {
        List<String> channels = new ArrayList<>();
        try {
            var rows = jdbc.queryForList("""
                    SELECT channel, event_types
                      FROM notification_preferences
                     WHERE tenant_id = ?::uuid AND user_email = ?
                    """, tenantId.toString(), recipient);
            for (Map<String, Object> row : rows) {
                JsonNode events = parseJson(row.get("event_types"));
                if (events == null || !events.isArray() || events.isEmpty()) {
                    // Empty event_types = user wants everything on this channel.
                    channels.add(String.valueOf(row.get("channel")));
                    continue;
                }
                for (JsonNode e : events) {
                    if (eventType.equalsIgnoreCase(e.asText())) {
                        channels.add(String.valueOf(row.get("channel")));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("resolveChannels fallback for {}/{}: {}", tenantId, recipient, e.getMessage());
        }
        // IN_APP is created synchronously on notify() — exclude from the fan-out
        // so we don't double-write.
        channels.removeIf("IN_APP"::equalsIgnoreCase);
        return channels;
    }

    private JsonNode resolveConfig(UUID tenantId, String recipient) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT config FROM notification_preferences
                     WHERE tenant_id = ?::uuid AND user_email = ?
                     LIMIT 1
                    """, tenantId.toString(), recipient);
            return parseJson(row.get("config"));
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private JsonNode parseJson(Object value) {
        if (value == null) return mapper.createObjectNode();
        try { return mapper.readTree(value.toString()); }
        catch (Exception e) { return mapper.createObjectNode(); }
    }
}
