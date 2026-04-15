package com.nexoai.ontology.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Periodically wakes up workflow runs that were paused by a long WAIT step
 * (see {@link com.nexoai.ontology.core.workflow.steps.WaitStep}). Runs whose
 * resume_at has passed are picked up, the executor loop continues from
 * current_step, and the run is marked COMPLETED / FAILED at the end.
 *
 * Uses SELECT ... FOR UPDATE SKIP LOCKED so multi-instance deployments don't
 * race for the same paused run.
 */
@Component
@Slf4j
public class WorkflowResumeScheduler {

    private static final int BATCH_SIZE = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Map<String, WorkflowStepExecutor> executors;

    @Value("${nexo.workflow.resume.enabled:true}")
    private boolean enabled;

    public WorkflowResumeScheduler(JdbcTemplate jdbc, ObjectMapper mapper,
                                    List<WorkflowStepExecutor> executorList) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.executors = executorList.stream()
                .collect(Collectors.toMap(WorkflowStepExecutor::stepType, e -> e, (a, b) -> a));
    }

    @Scheduled(fixedDelay = 30_000)
    public void resumeDueRuns() {
        if (!enabled) return;
        List<Map<String, Object>> due;
        try {
            due = jdbc.queryForList("""
                    SELECT id FROM workflow_runs
                     WHERE status = 'PAUSED' AND resume_at <= NOW()
                     ORDER BY resume_at
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                    """, BATCH_SIZE);
        } catch (Exception e) {
            log.debug("resume scheduler skipped: {}", e.getMessage());
            return;
        }
        for (Map<String, Object> row : due) {
            UUID runId = UUID.fromString(row.get("id").toString());
            try {
                resumeRun(runId);
            } catch (Exception e) {
                log.warn("Resume failed for {}: {}", runId, e.getMessage());
                jdbc.update("""
                        UPDATE workflow_runs
                           SET status = 'FAILED', finished_at = NOW(), error_message = ?
                         WHERE id = ?::uuid
                        """, "resume failed: " + e.getMessage(), runId.toString());
            }
        }
    }

    @Transactional
    void resumeRun(UUID runId) throws Exception {
        Map<String, Object> run = jdbc.queryForMap(
                "SELECT * FROM workflow_runs WHERE id = ?::uuid", runId.toString());
        UUID workflowId = UUID.fromString(run.get("workflow_id").toString());
        int currentStep = ((Number) run.getOrDefault("current_step", 0)).intValue();

        Map<String, Object> workflow = jdbc.queryForMap(
                "SELECT * FROM workflows WHERE id = ?::uuid", workflowId.toString());

        // Reconstruct context from persisted trigger_data + accumulated step outputs.
        WorkflowContext ctx = new WorkflowContext(mapper);
        Object triggerData = run.get("trigger_data");
        ctx.put("triggerData", mapper.readTree(
                triggerData == null ? "{}" : triggerData.toString()));
        Object existingResults = run.get("step_results");
        if (existingResults != null) {
            JsonNode arr = mapper.readTree(existingResults.toString());
            if (arr.isArray()) {
                int idx = 0;
                for (JsonNode entry : arr) {
                    JsonNode out = entry.path("output");
                    if (!out.isMissingNode()) ctx.put("step_" + idx, out);
                    idx++;
                }
            }
        }

        List<JsonNode> steps = parseSteps(workflow.get("steps"));
        List<Map<String, Object>> stepResults = new ArrayList<>();
        if (existingResults != null) {
            JsonNode arr = mapper.readTree(existingResults.toString());
            if (arr.isArray()) {
                arr.forEach(n -> stepResults.add(mapper.convertValue(n, Map.class)));
            }
        }

        String finalStatus = "COMPLETED";
        for (int i = currentStep; i < steps.size(); i++) {
            JsonNode step = steps.get(i);
            String type = step.path("type").asText("LOG");
            String name = step.path("name").asText("step-" + (i + 1));

            WorkflowStepExecutor exec = executors.get(type);
            long t0 = System.currentTimeMillis();
            if (exec == null) {
                stepResults.add(entry(i, name, type, "FAILED", null,
                        System.currentTimeMillis() - t0, "unknown step type: " + type));
                finalStatus = "FAILED";
                break;
            }

            try {
                WorkflowStepExecutor.StepResult r = exec.execute(step, ctx);
                long duration = System.currentTimeMillis() - t0;
                stepResults.add(entry(i, name, type, r.status(), r.output(), duration, r.error()));
                if (r.output() != null) ctx.put("step_" + i, r.output());

                if ("PAUSE".equals(r.status())) {
                    // Another long WAIT inside the resumed run — re-pause.
                    long resumeMs = r.output() == null ? 0
                            : r.output().path("pauseUntilMs").asLong(0);
                    jdbc.update("""
                            UPDATE workflow_runs
                               SET status = 'PAUSED',
                                   step_results = ?::jsonb,
                                   current_step = ?,
                                   resume_at = to_timestamp(?)
                             WHERE id = ?::uuid
                            """,
                            mapper.writeValueAsString(stepResults),
                            i + 1, resumeMs / 1000.0, runId.toString());
                    return;
                }
                if ("FAILED".equals(r.status())) {
                    finalStatus = "FAILED";
                    break;
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - t0;
                stepResults.add(entry(i, name, type, "FAILED", null, duration, e.getMessage()));
                finalStatus = "FAILED";
                break;
            }
        }

        jdbc.update("""
                UPDATE workflow_runs
                   SET status = ?, step_results = ?::jsonb,
                       finished_at = NOW(), resume_at = NULL
                 WHERE id = ?::uuid
                """,
                finalStatus, mapper.writeValueAsString(stepResults), runId.toString());
        log.info("Resumed run {} {} (steps total {})", runId, finalStatus, stepResults.size());
    }

    private List<JsonNode> parseSteps(Object stepsJson) throws Exception {
        if (stepsJson == null) return List.of();
        JsonNode node = mapper.readTree(stepsJson.toString());
        if (!node.isArray()) return List.of();
        List<JsonNode> out = new ArrayList<>();
        node.forEach(out::add);
        return out;
    }

    private static Map<String, Object> entry(int idx, String name, String type,
                                              String status, JsonNode output,
                                              long durationMs, String error) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("index", idx);
        m.put("step", name);
        m.put("type", type);
        m.put("status", status);
        m.put("durationMs", durationMs);
        if (output != null) m.put("output", output);
        if (error != null) m.put("error", error);
        m.put("executedAt", OffsetDateTime.now().toString());
        return m;
    }
}
