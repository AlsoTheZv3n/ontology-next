package com.nexoai.ontology.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

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

    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerWorkflow(UUID workflowId, String triggerData) {
        Map<String, Object> workflow = getWorkflow(workflowId);

        if (!Boolean.TRUE.equals(workflow.get("is_active"))) {
            throw new OntologyException("Workflow is not active: " + workflowId);
        }

        UUID runId = UUID.randomUUID();
        UUID tenantId = UUID.fromString(workflow.get("tenant_id").toString());

        jdbcTemplate.update(
                """
                INSERT INTO workflow_runs (id, workflow_id, tenant_id, status, trigger_data)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'RUNNING', ?::jsonb)
                """,
                runId.toString(), workflowId.toString(), tenantId.toString(),
                triggerData != null ? triggerData : "{}");

        try {
            String stepsStr = workflow.get("steps").toString();
            List<Map<String, Object>> steps = objectMapper.readValue(stepsStr, List.class);
            List<Map<String, Object>> stepResults = new ArrayList<>();

            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> step = steps.get(i);
                String stepName = step.getOrDefault("name", "step-" + (i + 1)).toString();
                String stepType = step.getOrDefault("type", "LOG").toString();

                Map<String, Object> result = new HashMap<>();
                result.put("step", stepName);
                result.put("type", stepType);
                result.put("index", i);
                result.put("status", "COMPLETED");
                result.put("executedAt", OffsetDateTime.now().toString());

                log.info("Workflow run {}: executing step {} ({})", runId, stepName, stepType);
                stepResults.add(result);
            }

            String stepResultsJson = objectMapper.writeValueAsString(stepResults);

            jdbcTemplate.update(
                    """
                    UPDATE workflow_runs
                    SET status = 'COMPLETED', step_results = ?::jsonb, finished_at = NOW()
                    WHERE id = ?::uuid
                    """,
                    stepResultsJson, runId.toString());

            log.info("Workflow run {} completed with {} steps", runId, steps.size());

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
}
