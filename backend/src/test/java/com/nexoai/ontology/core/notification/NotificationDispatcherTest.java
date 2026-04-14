package com.nexoai.ontology.core.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.nexoai.ontology.core.notification.channels.SlackChannel;
import com.nexoai.ontology.core.notification.channels.TeamsChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the notification pipeline: the Slack and Teams channels
 * post to a local HttpServer, and the dispatcher routes an outgoing request across
 * multiple channels. No mocking of HttpClient — we hit a real loopback server so
 * the JSON payload shape is actually verified on the wire.
 */
class NotificationDispatcherTest {

    private HttpServer server;
    private int port;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);

    private ObjectMapper mapper;

    @BeforeEach
    void startServer() throws Exception {
        mapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastBody.set(new String(body, StandardCharsets.UTF_8));
            hits.incrementAndGet();
            int code = nextStatus.get();
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    private NotificationChannel.NotificationRequest req(String title) {
        return new NotificationChannel.NotificationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "sync_failure",
                "alerts@nexo.ai", title, "Something went wrong",
                mapper.createObjectNode());
    }

    private JsonNode slackCfg() {
        var n = mapper.createObjectNode();
        n.put("webhookUrl", "http://127.0.0.1:" + port + "/hook");
        return n;
    }

    @Test
    void slack_channel_posts_block_kit_payload() throws Exception {
        SlackChannel slack = new SlackChannel(mapper, HttpClient.newHttpClient());

        var r = slack.send(req("Sync failed"), slackCfg());

        assertThat(r.success()).isTrue();
        assertThat(hits.get()).isEqualTo(1);
        JsonNode body = mapper.readTree(lastBody.get());
        assertThat(body.path("text").asText()).isEqualTo("Sync failed");
        assertThat(body.path("blocks").isArray()).isTrue();
        assertThat(body.path("blocks").size()).isEqualTo(3);
    }

    @Test
    void slack_channel_fails_gracefully_on_500_response() {
        nextStatus.set(500);
        SlackChannel slack = new SlackChannel(mapper, HttpClient.newHttpClient());

        var r = slack.send(req("x"), slackCfg());

        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("500");
    }

    @Test
    void slack_channel_fails_when_webhookUrl_missing() {
        SlackChannel slack = new SlackChannel(mapper, HttpClient.newHttpClient());
        var r = slack.send(req("x"), mapper.createObjectNode());
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("webhookUrl");
    }

    @Test
    void teams_channel_posts_message_card() throws Exception {
        TeamsChannel teams = new TeamsChannel(mapper, HttpClient.newHttpClient());
        var cfg = mapper.createObjectNode();
        cfg.put("webhookUrl", "http://127.0.0.1:" + port + "/hook");

        var r = teams.send(req("High revenue"), cfg);

        assertThat(r.success()).isTrue();
        JsonNode body = mapper.readTree(lastBody.get());
        assertThat(body.path("@type").asText()).isEqualTo("MessageCard");
        assertThat(body.path("title").asText()).isEqualTo("High revenue");
    }

    @Test
    void dispatcher_fans_out_to_multiple_channels() throws Exception {
        MeterRegistry metrics = new SimpleMeterRegistry();
        SlackChannel slack = new SlackChannel(mapper, HttpClient.newHttpClient());
        TeamsChannel teams = new TeamsChannel(mapper, HttpClient.newHttpClient());
        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(slack, teams), metrics);

        var cfg = mapper.createObjectNode();
        var chCfg = mapper.createObjectNode();
        chCfg.put("webhookUrl", "http://127.0.0.1:" + port + "/hook");
        cfg.set("slack", chCfg);
        cfg.set("teams", chCfg);

        var outcomes = dispatcher.dispatch(req("test"), List.of("SLACK", "TEAMS"), cfg);

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes).allMatch(NotificationDispatcher.DeliveryOutcome::success);
        assertThat(hits.get()).isEqualTo(2);
        assertThat(metrics.counter("nexo.notifications.sent", "channel", "SLACK", "status", "ok").count())
                .isEqualTo(1);
    }

    @Test
    void dispatcher_reports_unknown_channel_as_failed() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(), new SimpleMeterRegistry());
        var outcomes = dispatcher.dispatch(req("x"), List.of("BOGUS"), mapper.createObjectNode());
        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).success()).isFalse();
        assertThat(outcomes.get(0).error()).contains("not registered");
    }

    @Test
    void dispatcher_returns_empty_when_no_channels_enabled() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(), new SimpleMeterRegistry());
        var outcomes = dispatcher.dispatch(req("x"), List.of(), mapper.createObjectNode());
        assertThat(outcomes).isEmpty();
    }
}
