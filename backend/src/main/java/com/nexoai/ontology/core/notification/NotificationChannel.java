package com.nexoai.ontology.core.notification;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * One delivery target (Slack, Teams, Email, InApp, ...). The dispatcher routes
 * each pending notification to the channels enabled for its tenant + event type
 * and records per-channel success/failure back onto the notification row.
 *
 * Implementations MUST NOT throw on transport failure — return a DeliveryResult
 * with success=false and an error message so the dispatcher can track retries.
 */
public interface NotificationChannel {

    /** Channel identifier stored in notification_preferences.channel. */
    String name();

    DeliveryResult send(NotificationRequest req, JsonNode channelConfig);

    record DeliveryResult(boolean success, String externalId, String error) {
        public static DeliveryResult ok()             { return new DeliveryResult(true, null, null); }
        public static DeliveryResult ok(String id)    { return new DeliveryResult(true, id, null); }
        public static DeliveryResult fail(String err) { return new DeliveryResult(false, null, err); }
    }

    record NotificationRequest(
            UUID id,
            UUID tenantId,
            String eventType,
            String recipient,
            String title,
            String body,
            JsonNode context
    ) {}
}
