package com.nexoai.ontology.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Strategy interface for one workflow step type. Each implementation declares the
 * step type it handles and runs the step against the current WorkflowContext.
 *
 * Return a StepResult with status OK, SKIPPED, or FAILED. Throwing is also allowed:
 * the WorkflowService catches and maps to FAILED with the exception message.
 */
public interface WorkflowStepExecutor {

    /** Step type identifier that appears in the workflow definition JSON ("type" field). */
    String stepType();

    StepResult execute(JsonNode stepConfig, WorkflowContext ctx);

    record StepResult(String status, JsonNode output, String error) {
        public static StepResult ok(JsonNode out) { return new StepResult("OK", out, null); }
        public static StepResult skip(String reason) { return new StepResult("SKIPPED", null, reason); }
        public static StepResult fail(String err) { return new StepResult("FAILED", null, err); }
    }
}
