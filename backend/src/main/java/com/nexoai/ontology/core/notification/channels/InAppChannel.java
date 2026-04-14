package com.nexoai.ontology.core.notification.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.notification.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes the notification to the in-app feed (notifications table with is_read=false).
 * This is the default channel every tenant has even without external webhooks configured.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InAppChannel implements NotificationChannel {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper;

    @Override public String name() { return "IN_APP"; }

    @Override
    public DeliveryResult send(NotificationRequest req, JsonNode cfg) {
        try {
            String meta = req.context() == null ? "{}" : mapper.writeValueAsString(req.context());
            jdbcTemplate.update(
                    """
                    INSERT INTO notifications (id, tenant_id, user_email, type, title, message, metadata)
                    VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    req.id().toString(), req.tenantId().toString(),
                    req.recipient(), req.eventType(), req.title(), req.body(), meta);
            return DeliveryResult.ok(req.id().toString());
        } catch (Exception e) {
            log.warn("In-app delivery failed: {}", e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        }
    }
}
