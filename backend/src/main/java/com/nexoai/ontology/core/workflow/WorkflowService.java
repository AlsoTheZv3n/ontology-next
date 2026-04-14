package com.nexoai.ontology.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkflowService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, WorkflowStepExecutor> executors;

    public WorkflowService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                            List<WorkflowStepExecutor> executorList) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.executors = executorList.stream()
                .collect(Collectors.toMap(WorkflowStepExecutor::stepType, e -> e, (a, b) -> a));
    }

    public Map<String, Object> createWorkflow(UUID tenantId, String name, String description,
                                               String triggerType, String triggerConfig, String steps) {
        UUID workflowId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO workflows (id, tenant_id, name, description, trigger_type, trigger_config, steps)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?::jsonb, ?::jsonb)
                """,
                workflowId.toString(), tenantId.toString(), name, description,
                triggerType,
                triggerConfig != null ? triggerConfig : "{}",
                steps != null ? steps : "[]");

        log.info("Workflow created: {} for tenant {}", name, tenantId);

        return jdbcTemplate.queryForMap(
                "SELECT * FROM workflows WHERE id = ?::uuid", workflowId.toString());
    }

    public List<Map<String, Object>> listWorkflows(UUID tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM workflows WHERE tenant_id = ?::uuid ORDER BY created_at DESC",
                tenantId.toString());
    }

    public Map<String, Object> getWorkflow(UUID workflowId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT * FROM workflows WHERE id = ?::uuid", workflowId.toString());
        } catch (Exception e) {
            throw new OntologyException("Workflow not found: " + workflowId);
        }
    }

    public Map<String, Object> triggerWorkflow(UUID workflowId, String triggerData) {
        Map<String, Object> workflow = getWorkflow(workflowId);

        if (!Boolean.TRUE.equals(workflow.get("is_active"))) {
            throw new OntologyException("Workflow is not active: " + workflowId);
        }

        UUID runId = UUID.randomUUID();
        UUID tenantId = UUID.fromString(workflow.get("tenant_id").toString());
        String triggerJson = triggerData != null ? triggerData : "{}";

        jdbcTemplate.update(
                """
                INSERT INTO workflow_runs (id, workflow_id, tenant_id, status, trigger_data)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'RUNNING', ?::jsonb)
                """,
                runId.toString(), workflowId.toString(), tenantId.toString(), triggerJson);

        List<Map<String, Object>> stepResults = new ArrayList<>();
        String finalStatus = "COMPLETED";

        try {
            WorkflowContext ctx = new WorkflowContext(objectMapper);
            ctx.put("triggerData", objectMapper.readTree(triggerJson));

            List<JsonNode> steps = parseSteps(workflow.get("steps"));

            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                String type = step.path("type").asText("LOG");
                String name = step.path("name").asText("step-" + (i + 1));

                if (ctx.isSkipRest()) {
                    stepResults.add(stepEntry(i, name, type, "SKIPPED", null,
                            0L, "previous condition false"));
                    continue;
                }

                WorkflowStepExecutor exec = executors.get(type);
                long t0 = System.currentTimeMillis();
                if (exec == null) {
                    stepResults.add(stepEntry(i, name, type, "FAILED", null,
                            System.currentTimeMillis() - t0, "unknown step type: " + type));
                    finalStatus = "FAILED";
                    break;
                }

                try {
                    WorkflowStepExecutor.StepResult r = exec.execute(step, ctx);
                    long duration = System.currentTimeMillis() - t0;
                    stepResults.add(stepEntry(i, name, type, r.status(), r.output(), duration, r.error()));
                    if (r.output() != null) ctx.put("step_" + i, r.output());
                    if ("FAILED".equals(r.status())) {
                        finalStatus = "FAILED";
                        break;
                    }
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - t0;
                    stepResults.add(stepEntry(i, name, type, "FAILED", null, duration, e.getMessage()));
                    finalStatus = "FAILED";
                    break;
                }
            }

            jdbcTemplate.update(
                    """
                    UPDATE workflow_runs
                    SET status = ?, step_results = ?::jsonb, finished_at = NOW()
                    WHERE id = ?::uuid
                    """,
                    finalStatus, objectMapper.writeValueAsString(stepResults), runId.toString());

            log.info("Workflow run {} {} with {} steps", runId, finalStatus, stepResults.size());

        } catch (OntologyException e) {
            throw e;
        } catch (Exception e) {
            jdbcTemplate.update(
                    """
                    UPDATE workflow_runs
                    SET status = 'FAILED', finished_at = NOW(), error_message = ?
                    WHERE id = ?::uuid
                    """,
                    e.getMessage(), runId.toString());
            log.error("Workflow run {} failed: {}", runId, e.getMessage());
        }

        return jdbcTemplate.queryForMap(
                "SELECT * FROM workflow_runs WHERE id = ?::uuid", runId.toString());
    }

    public List<Map<String, Object>> getRunHistory(UUID workflowId) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM workflow_runs
                WHERE workflow_id = ?::uuid
                ORDER BY started_at DESC
                LIMIT 100
                """,
                workflowId.toString());
    }

    private List<JsonNode> parseSteps(Object stepsJson) throws Exception {
        if (stepsJson == null) return List.of();
        JsonNode node = objectMapper.readTree(stepsJson.toString());
        if (!node.isArray()) return List.of();
        List<JsonNode> out = new ArrayList<>();
        node.forEach(out::add);
        return out;
    }

    private static Map<String, Object> stepEntry(int index, String name, String type,
                                                  String status, JsonNode output,
                                                  long durationMs, String error) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("index", index);
        entry.put("step", name);
        entry.put("type", type);
        entry.put("status", status);
        entry.put("durationMs", durationMs);
        if (output != null) entry.put("output", output);
        if (error != null) entry.put("error", error);
        entry.put("executedAt", OffsetDateTime.now().toString());
        return entry;
    }
}
