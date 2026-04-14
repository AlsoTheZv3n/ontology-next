package com.nexoai.ontology.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.workflow.steps.ConditionStep;
import com.nexoai.ontology.core.workflow.steps.LogStep;
import com.nexoai.ontology.core.workflow.steps.WaitStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises WorkflowService.triggerWorkflow end-to-end using real step executors
 * and a mocked JdbcTemplate. The interesting surface is the dispatch loop,
 * skipRest semantics, step_result shape, and failure propagation.
 */
class WorkflowServiceTest {

    private JdbcTemplate jdbc;
    private ObjectMapper mapper;
    private WorkflowService service;
    private UUID workflowId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        mapper = new ObjectMapper();
        service = new WorkflowService(jdbc, mapper,
                List.of(new LogStep(mapper), new ConditionStep(mapper), new WaitStep(mapper)));
        workflowId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
    }

    private void stubWorkflow(String stepsJson) {
        Map<String, Object> wf = new HashMap<>();
        wf.put("id", workflowId.toString());
        wf.put("tenant_id", tenantId.toString());
        wf.put("is_active", true);
        wf.put("steps", stepsJson);
        when(jdbc.queryForMap(contains("FROM workflows"), eq(workflowId.toString())))
                .thenReturn(wf);
        when(jdbc.queryForMap(contains("FROM workflow_runs"), any(String.class)))
                .thenReturn(Map.of("id", UUID.randomUUID().toString(), "status", "COMPLETED"));
    }

    @Test
    void log_step_produces_ok_result_with_output() throws Exception {
        stubWorkflow("[{\"type\":\"LOG\",\"name\":\"hello\",\"message\":\"hi\"}]");

        service.triggerWorkflow(workflowId, "{}");

        // Capture the UPDATE statement with step_results
        verify(jdbc).update(contains("UPDATE workflow_runs"),
                eq("COMPLETED"), argThat((String s) -> {
                    try {
                        JsonNode arr = mapper.readTree(s);
                        return arr.isArray() && arr.size() == 1
                                && "OK".equals(arr.get(0).path("status").asText())
                                && "LOG".equals(arr.get(0).path("type").asText())
                                && "hi".equals(arr.get(0).path("output").path("logged").asText());
                    } catch (Exception e) { return false; }
                }),
                anyString());
    }

    @Test
    void condition_false_skips_remaining_steps() throws Exception {
        stubWorkflow("""
                [{"type":"CONDITION","expression":"$.triggerData.revenue > 10000"},
                 {"type":"LOG","message":"should be skipped"}]
                """);

        service.triggerWorkflow(workflowId, "{\"revenue\":100}");

        verify(jdbc).update(contains("UPDATE workflow_runs"),
                eq("COMPLETED"), argThat((String s) -> {
                    try {
                        JsonNode arr = mapper.readTree(s);
                        return arr.size() == 2
                                && "OK".equals(arr.get(0).path("status").asText())
                                && !arr.get(0).path("output").path("matched").asBoolean()
                                && "SKIPPED".equals(arr.get(1).path("status").asText());
                    } catch (Exception e) { return false; }
                }),
                anyString());
    }

    @Test
    void condition_true_allows_following_steps_to_run() throws Exception {
        stubWorkflow("""
                [{"type":"CONDITION","expression":"$.triggerData.revenue > 10000"},
                 {"type":"LOG","message":"high revenue"}]
                """);

        service.triggerWorkflow(workflowId, "{\"revenue\":50000}");

        verify(jdbc).update(contains("UPDATE workflow_runs"),
                eq("COMPLETED"), argThat((String s) -> {
                    try {
                        JsonNode arr = mapper.readTree(s);
                        return arr.size() == 2
                                && arr.get(0).path("output").path("matched").asBoolean()
                                && "OK".equals(arr.get(1).path("status").asText());
                    } catch (Exception e) { return false; }
                }),
                anyString());
    }

    @Test
    void unknown_step_type_fails_run_and_skips_rest() throws Exception {
        stubWorkflow("""
                [{"type":"BOGUS"},
                 {"type":"LOG","message":"unreachable"}]
                """);

        service.triggerWorkflow(workflowId, "{}");

        verify(jdbc).update(contains("UPDATE workflow_runs"),
                eq("FAILED"), argThat((String s) -> {
                    try {
                        JsonNode arr = mapper.readTree(s);
                        return arr.size() == 1
                                && "FAILED".equals(arr.get(0).path("status").asText())
                                && arr.get(0).path("error").asText().contains("unknown step type");
                    } catch (Exception e) { return false; }
                }),
                anyString());
    }

    @Test
    void step_results_include_duration_and_executed_at() throws Exception {
        stubWorkflow("[{\"type\":\"WAIT\",\"durationMs\":20}]");

        service.triggerWorkflow(workflowId, "{}");

        verify(jdbc).update(contains("UPDATE workflow_runs"),
                eq("COMPLETED"), argThat((String s) -> {
                    try {
                        JsonNode entry = mapper.readTree(s).get(0);
                        return entry.path("durationMs").asLong() >= 20
                                && !entry.path("executedAt").asText().isBlank();
                    } catch (Exception e) { return false; }
                }),
                anyString());
    }

    @Test
    void inactive_workflow_refuses_to_run() {
        Map<String, Object> wf = new HashMap<>();
        wf.put("id", workflowId.toString());
        wf.put("tenant_id", tenantId.toString());
        wf.put("is_active", false);
        wf.put("steps", "[]");
        when(jdbc.queryForMap(contains("FROM workflows"), eq(workflowId.toString())))
                .thenReturn(wf);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> service.triggerWorkflow(workflowId, "{}"));
    }

    @Test
    void condition_supports_string_equality() {
        ConditionStep cs = new ConditionStep(mapper);
        WorkflowContext ctx = new WorkflowContext(mapper);
        try {
            ctx.put("triggerData", mapper.readTree("{\"status\":\"active\"}"));
        } catch (Exception e) { throw new RuntimeException(e); }
        JsonNode cfg;
        try {
            cfg = mapper.readTree("{\"expression\":\"$.triggerData.status == 'active'\"}");
        } catch (Exception e) { throw new RuntimeException(e); }

        var r = cs.execute(cfg, ctx);
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.output().path("matched").asBoolean()).isTrue();
        assertThat(ctx.isSkipRest()).isFalse();
    }
}
