package com.nexoai.ontology.core.workflow.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.workflow.WorkflowContext;
import com.nexoai.ontology.core.workflow.WorkflowStepExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/**
 * Workflow step that POSTs the workflow context (or a configured body) to an
 * external URL. Uses the JDK HttpClient — no Spring WebFlux dependency.
 *
 * Step config schema:
 *   {
 *     "type":    "WEBHOOK",
 *     "url":     "https://example.com/hook",
 *     "method":  "POST",                       (default POST)
 *     "body":    { ... }                       (optional, defaults to ctx.root())
 *     "headers": { "X-Auth": "..." },          (optional)
 *     "timeoutSeconds": 10                     (optional, default 10, max 30)
 *   }
 *
 * 4xx/5xx responses produce StepResult.fail with the status code so the workflow
 * run is marked FAILED and downstream steps are SKIPPED. The response body is
 * included in the step output for debugging (truncated at 1KB).
 */
@Component
@Slf4j
public class WebhookStep implements WorkflowStepExecutor {

    private static final int MAX_BODY_LOG = 1024;
    private static final int MAX_TIMEOUT_SECONDS = 30;

    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public WebhookStep(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public WebhookStep(ObjectMapper mapper, HttpClient httpClient) {
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    @Override public String stepType() { return "WEBHOOK"; }

    @Override
    public StepResult execute(JsonNode cfg, WorkflowContext ctx) {
        String url = cfg.path("url").asText("");
        if (url.isBlank()) return StepResult.fail("missing url");

        String method = cfg.path("method").asText("POST").toUpperCase();
        long timeoutSec = Math.min(cfg.path("timeoutSeconds").asLong(10), MAX_TIMEOUT_SECONDS);

        JsonNode payload = cfg.has("body") && !cfg.path("body").isMissingNode()
                ? cfg.path("body")
                : ctx.root();

        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(payload.toString()));

            JsonNode headers = cfg.path("headers");
            if (headers.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = headers.fields();
                while (it.hasNext()) {
                    var e = it.next();
                    b.header(e.getKey(), e.getValue().asText());
                }
            }

            HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
            ObjectNode out = mapper.createObjectNode();
            out.put("status", resp.statusCode());
            out.put("body", truncate(resp.body()));

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return StepResult.ok(out);
            }
            return new StepResult("FAILED", out, "HTTP " + resp.statusCode());
        } catch (Exception e) {
            log.warn("Webhook step failed for {}: {}", url, e.getMessage());
            return StepResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > MAX_BODY_LOG ? s.substring(0, MAX_BODY_LOG) + "…" : s;
    }
}
