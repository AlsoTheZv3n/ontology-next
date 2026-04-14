package com.nexoai.ontology.core.workflow.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.workflow.WorkflowContext;
import com.nexoai.ontology.core.workflow.WorkflowStepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Blocks the executing thread for durationMs (capped at 10s so a misconfigured
 * workflow can't monopolize the pool). Long waits should use a resume-later
 * scheduler instead — tracked as a future improvement.
 */
@Component
@RequiredArgsConstructor
public class WaitStep implements WorkflowStepExecutor {

    static final long MAX_WAIT_MS = 10_000L;

    private final ObjectMapper mapper;

    @Override public String stepType() { return "WAIT"; }

    @Override
    public StepResult execute(JsonNode cfg, WorkflowContext ctx) {
        long requested = cfg.path("durationMs").asLong(0);
        if (requested < 0) return StepResult.fail("durationMs must be >= 0");
        long ms = Math.min(requested, MAX_WAIT_MS);
        try {
            if (ms > 0) Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.fail("interrupted");
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("waitedMs", ms);
        if (ms < requested) out.put("cappedFrom", requested);
        return StepResult.ok(out);
    }
}
