package com.nexoai.ontology.core.workflow.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.workflow.WorkflowContext;
import com.nexoai.ontology.core.workflow.WorkflowStepExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes the configured message to the app log and records it in the step output.
 * Useful as a placeholder or debugging aid in larger workflows — the message is
 * echoed into workflow_runs.step_results so the UI timeline can show it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogStep implements WorkflowStepExecutor {

    private final ObjectMapper mapper;

    @Override public String stepType() { return "LOG"; }

    @Override
    public StepResult execute(JsonNode cfg, WorkflowContext ctx) {
        String message = cfg.path("message").asText("");
        log.info("[workflow] {}", message);
        ObjectNode out = mapper.createObjectNode();
        out.put("logged", message);
        return StepResult.ok(out);
    }
}
