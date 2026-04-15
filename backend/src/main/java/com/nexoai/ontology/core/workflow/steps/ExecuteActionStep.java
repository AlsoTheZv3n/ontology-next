package com.nexoai.ontology.core.workflow.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.service.action.ActionEngine;
import com.nexoai.ontology.core.workflow.WorkflowContext;
import com.nexoai.ontology.core.workflow.WorkflowStepExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Workflow step that invokes an {@link ActionEngine} action against the object
 * referenced by the workflow context (typically the trigger payload's id).
 *
 * Step config schema:
 *   {
 *     "type":         "EXECUTE_ACTION",
 *     "actionType":   "addTag",
 *     "objectIdExpr": "$.triggerData.id",      (default — JSON path against ctx.root)
 *     "params":       { "tag": "hot" }         (optional)
 *   }
 *
 * The objectIdExpr supports the same $.path syntax as ConditionStep (single-segment
 * dotted path). For more complex resolution use a precursor step that writes the
 * id into a known context slot.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExecuteActionStep implements WorkflowStepExecutor {

    private final ActionEngine actionEngine;
    private final ObjectMapper mapper;

    @Override public String stepType() { return "EXECUTE_ACTION"; }

    @Override
    public StepResult execute(JsonNode cfg, WorkflowContext ctx) {
        String actionType = cfg.path("actionType").asText("");
        if (actionType.isBlank()) return StepResult.fail("missing actionType");

        String expr = cfg.path("objectIdExpr").asText("$.triggerData.id");
        String idStr = resolvePath(ctx.root(), expr);
        if (idStr == null || idStr.isBlank()) {
            return StepResult.fail("could not resolve objectId from " + expr);
        }
        UUID objectId;
        try {
            objectId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return StepResult.fail("objectIdExpr resolved to non-UUID: " + idStr);
        }

        JsonNode params = cfg.path("params");
        try {
            var result = actionEngine.executeAction(actionType, objectId,
                    params.isMissingNode() ? mapper.createObjectNode() : params,
                    "workflow");
            return StepResult.ok(mapper.valueToTree(result));
        } catch (Exception e) {
            log.warn("EXECUTE_ACTION step failed: action={} object={}: {}",
                    actionType, objectId, e.getMessage());
            return StepResult.fail(e.getMessage());
        }
    }

    /** Walks a $.a.b.c expression. Same shape as ConditionStep's resolver. */
    static String resolvePath(JsonNode root, String expr) {
        if (expr == null || !expr.startsWith("$.")) return null;
        JsonNode cur = root;
        for (String seg : expr.substring(2).split("\\.")) {
            if (cur == null || cur.isNull()) return null;
            cur = cur.get(seg);
        }
        return cur == null || cur.isNull() ? null : cur.asText();
    }
}
