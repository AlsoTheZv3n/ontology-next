package com.nexoai.ontology.core.workflow.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.notification.NotificationChannel;
import com.nexoai.ontology.core.notification.NotificationDispatcher;
import com.nexoai.ontology.core.domain.action.ActionResult;
import com.nexoai.ontology.core.service.action.ActionEngine;
import com.nexoai.ontology.core.workflow.WorkflowContext;
import com.nexoai.ontology.core.workflow.WorkflowStepExecutor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Coverage for the three workflow executors that landed in gap-03:
 * NotifyStep, WebhookStep, ExecuteActionStep.
 *
 * WebhookStep uses a real loopback HttpServer instead of mocking the JDK
 * HttpClient — the on-the-wire payload shape is what matters.
 */
class NewStepsTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() { mapper = new ObjectMapper(); }

    private WorkflowContext ctx() { return new WorkflowContext(mapper); }

    // -- NotifyStep --------------------------------------------------

    @Test
    void notify_step_dispatches_to_configured_channels_and_returns_OK_on_success() throws Exception {
        var dispatcher = mock(NotificationDispatcher.class);
        when(dispatcher.dispatch(any(), eq(List.of("SLACK")), any()))
                .thenReturn(List.of(new NotificationDispatcher.DeliveryOutcome("SLACK", true, null)));
        var step = new NotifyStep(dispatcher, mapper);
        var cfg = mapper.readTree("""
                {"channels":["SLACK"],"recipient":"x","title":"hi","body":"hello",
                 "config":{"slack":{"webhookUrl":"u"}}}
                """);

        var r = step.execute(cfg, ctx());

        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.output().path("anySuccess").asBoolean()).isTrue();
        verify(dispatcher).dispatch(
                argThat((NotificationChannel.NotificationRequest req) ->
                        "hi".equals(req.title()) && "x".equals(req.recipient())),
                eq(List.of("SLACK")), any());
    }

    @Test
    void notify_step_returns_FAILED_when_all_channels_fail() throws Exception {
        var dispatcher = mock(NotificationDispatcher.class);
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(List.of(new NotificationDispatcher.DeliveryOutcome("SLACK", false, "500")));
        var step = new NotifyStep(dispatcher, mapper);
        var cfg = mapper.readTree("{\"channels\":[\"SLACK\"]}");

        var r = step.execute(cfg, ctx());
        assertThat(r.status()).isEqualTo("FAILED");
    }

    @Test
    void notify_step_rejects_missing_channels() throws Exception {
        var step = new NotifyStep(mock(NotificationDispatcher.class), mapper);
        var r = step.execute(mapper.readTree("{}"), ctx());
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.error()).contains("channels");
    }

    // -- WebhookStep -------------------------------------------------

    @Test
    void webhook_step_posts_body_and_returns_OK_on_2xx() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", x -> {
            received.set(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"ok\":true}".getBytes();
            x.sendResponseHeaders(200, resp.length);
            x.getResponseBody().write(resp);
            x.close();
        });
        server.start();
        try {
            var step = new WebhookStep(mapper, HttpClient.newHttpClient());
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            var cfg = mapper.readTree("{\"url\":\"" + url + "\",\"body\":{\"alert\":\"x\"}}");

            var r = step.execute(cfg, ctx());

            assertThat(r.status()).isEqualTo("OK");
            assertThat(r.output().path("status").asInt()).isEqualTo(200);
            assertThat(received.get()).contains("\"alert\":\"x\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webhook_step_returns_FAILED_on_5xx() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", x -> {
            byte[] resp = "boom".getBytes();
            x.sendResponseHeaders(500, resp.length);
            x.getResponseBody().write(resp);
            x.close();
        });
        server.start();
        try {
            var step = new WebhookStep(mapper, HttpClient.newHttpClient());
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            var cfg = mapper.readTree("{\"url\":\"" + url + "\"}");

            var r = step.execute(cfg, ctx());

            assertThat(r.status()).isEqualTo("FAILED");
            assertThat(r.error()).contains("500");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webhook_step_rejects_missing_url() throws Exception {
        var step = new WebhookStep(mapper, HttpClient.newHttpClient());
        var r = step.execute(mapper.readTree("{}"), ctx());
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.error()).contains("url");
    }

    // -- ExecuteActionStep -------------------------------------------

    @Test
    void execute_action_step_calls_action_engine_with_resolved_objectId() throws Exception {
        var engine = mock(ActionEngine.class);
        UUID resultObjectId = UUID.randomUUID();
        when(engine.executeAction(eq("addTag"), any(UUID.class), any(JsonNode.class), eq("workflow")))
                .thenReturn(ActionResult.success(resultObjectId, UUID.randomUUID()));
        var step = new ExecuteActionStep(engine, mapper);

        UUID objectId = UUID.randomUUID();
        var ctx = ctx();
        ctx.put("triggerData", mapper.readTree("{\"id\":\"" + objectId + "\"}"));
        var cfg = mapper.readTree("{\"actionType\":\"addTag\",\"params\":{\"tag\":\"hot\"}}");

        var r = step.execute(cfg, ctx);

        assertThat(r.status()).isEqualTo("OK");
        verify(engine).executeAction(eq("addTag"), eq(objectId), any(), eq("workflow"));
    }

    @Test
    void execute_action_step_fails_on_unresolvable_objectIdExpr() throws Exception {
        var step = new ExecuteActionStep(mock(ActionEngine.class), mapper);
        var cfg = mapper.readTree("{\"actionType\":\"addTag\"}"); // expr defaults to $.triggerData.id, which isn't set

        var r = step.execute(cfg, ctx());
        assertThat(r.status()).isEqualTo("FAILED");
    }

    @Test
    void execute_action_step_fails_on_non_uuid_id() throws Exception {
        var step = new ExecuteActionStep(mock(ActionEngine.class), mapper);
        var ctx = ctx();
        ctx.put("triggerData", mapper.readTree("{\"id\":\"not-a-uuid\"}"));
        var cfg = mapper.readTree("{\"actionType\":\"addTag\"}");

        var r = step.execute(cfg, ctx);
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.error()).contains("non-UUID");
    }

    @Test
    void execute_action_step_rejects_missing_actionType() throws Exception {
        var step = new ExecuteActionStep(mock(ActionEngine.class), mapper);
        var r = step.execute(mapper.readTree("{}"), ctx());
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.error()).contains("actionType");
    }
}
