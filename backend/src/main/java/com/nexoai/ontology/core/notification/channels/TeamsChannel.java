package com.nexoai.ontology.core.notification.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.notification.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Posts an Office 365 connector-compatible MessageCard to a Teams Incoming Webhook.
 * Same pattern as SlackChannel: HttpClient is injectable so tests can redirect
 * traffic at a local server without mocking the whole network stack.
 */
@Component
@Slf4j
public class TeamsChannel implements NotificationChannel {

    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public TeamsChannel(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public TeamsChannel(ObjectMapper mapper, HttpClient httpClient) {
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    @Override public String name() { return "TEAMS"; }

    @Override
    public DeliveryResult send(NotificationRequest req, JsonNode cfg) {
        String url = cfg == null ? "" : cfg.path("webhookUrl").asText("");
        if (url.isBlank()) return DeliveryResult.fail("no webhookUrl configured");

        Map<String, Object> payload = Map.of(
                "@type", "MessageCard",
                "@context", "https://schema.org/extensions",
                "summary", req.title(),
                "themeColor", "0076D7",
                "title", req.title(),
                "text", req.body() == null ? "" : req.body()
        );
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> resp = httpClient.send(r, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return DeliveryResult.ok();
            return DeliveryResult.fail("HTTP " + resp.statusCode());
        } catch (Exception e) {
            log.warn("Teams delivery failed: {}", e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        }
    }
}
