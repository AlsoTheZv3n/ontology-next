package com.nexoai.ontology.core.notification.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.notification.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Posts a Block Kit message to a Slack Incoming Webhook URL. No extra dependencies —
 * uses the JDK HttpClient so unit tests can swap it for a local stub server.
 * Transport failures never throw: return a DeliveryResult with success=false so
 * the dispatcher's retry bookkeeping stays consistent across channels.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SlackChannel implements NotificationChannel {

    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public SlackChannel(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    @Override public String name() { return "SLACK"; }

    @Override
    public DeliveryResult send(NotificationRequest req, JsonNode cfg) {
        String url = cfg == null ? "" : cfg.path("webhookUrl").asText("");
        if (url.isBlank()) return DeliveryResult.fail("no webhookUrl configured");

        Map<String, Object> payload = Map.of(
                "text", req.title(),
                "blocks", List.of(
                        Map.of("type", "header", "text",
                                Map.of("type", "plain_text", "text", req.title(), "emoji", true)),
                        Map.of("type", "section", "text",
                                Map.of("type", "mrkdwn", "text", req.body() == null ? "" : req.body())),
                        Map.of("type", "context", "elements", List.of(
                                Map.of("type", "mrkdwn",
                                        "text", "Event: `" + req.eventType() + "` | Tenant `"
                                                + req.tenantId() + "`")))
                )
        );
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(r, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return DeliveryResult.ok();
            return DeliveryResult.fail("HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
        } catch (Exception e) {
            log.warn("Slack delivery failed: {}", e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
