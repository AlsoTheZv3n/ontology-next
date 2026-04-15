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

    /** Cap on inline waits — anything longer than this is converted to a PAUSE. */
    static final long INLINE_WAIT_THRESHOLD_MS = 2_000L;
    /** Hard cap on inline waits even when PAUSE is unavailable (defence in depth). */
    static final long MAX_INLINE_MS = 10_000L;
    /** Special StepResult status that tells WorkflowService to persist + resume later. */
    public static final String STATUS_PAUSE = "PAUSE";

    private final ObjectMapper mapper;

    @Override public String stepType() { return "WAIT"; }

    @Override
    public StepResult execute(JsonNode cfg, WorkflowContext ctx) {
        long requested = cfg.path("durationMs").asLong(0);
        if (requested < 0) return StepResult.fail("durationMs must be >= 0");

        // Long waits are deferred: emit a PAUSE result with a resume timestamp.
        // WorkflowService recognises the special status and persists the run as
        // PAUSED with resume_at — WorkflowResumeScheduler picks it up later.
        if (requested > INLINE_WAIT_THRESHOLD_MS) {
            ObjectNode out = mapper.createObjectNode();
            out.put("waitedMs", 0);
            out.put("pauseUntilMs", System.currentTimeMillis() + requested);
            out.put("requestedMs", requested);
            return new StepResult(STATUS_PAUSE, out, null);
        }

        long ms = Math.min(requested, MAX_INLINE_MS);
        try {
            if (ms > 0) Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.fail("interrupted");
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("waitedMs", ms);
        return StepResult.ok(out);
    }
}
