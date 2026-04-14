package com.nexoai.ontology.core.workflow.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.workflow.WorkflowContext;
import com.nexoai.ontology.core.workflow.WorkflowStepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates a boolean expression against the workflow context. Supported form:
 *   $.path.to.value <op> <literal>
 * where <op> is one of >, <, >=, <=, ==, != and <literal> is a number, a
 * single/double-quoted string, true, false, or null.
 *
 * If the expression evaluates false, the context is marked to skip remaining
 * steps (they appear as SKIPPED in the run report).
 *
 * Deliberately not using a full JSONPath lib — workflow expressions stay
 * readable and audit-friendly when the grammar is this narrow.
 */
@Component
@RequiredArgsConstructor
public class ConditionStep implements WorkflowStepExecutor {

    private static final Pattern EXPR = Pattern.compile(
            "^\\s*\\$\\.([\\w.\\[\\]]+)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*$");

    private final ObjectMapper mapper;

    @Override public String stepType() { return "CONDITION"; }

    @Override
    public StepResult execute(JsonNode cfg, WorkflowContext ctx) {
        String expr = cfg.path("expression").asText("");
        if (expr.isBlank()) return StepResult.fail("missing expression");

        boolean matched;
        try {
            matched = evaluate(expr, ctx.root());
        } catch (IllegalArgumentException e) {
            return StepResult.fail(e.getMessage());
        }

        if (!matched) ctx.markSkipRest();
        ObjectNode out = mapper.createObjectNode();
        out.put("matched", matched);
        out.put("expression", expr);
        return StepResult.ok(out);
    }

    private boolean evaluate(String expr, JsonNode root) {
        Matcher m = EXPR.matcher(expr);
        if (!m.matches()) throw new IllegalArgumentException("unparseable expression: " + expr);
        JsonNode lhs = resolvePath(root, m.group(1));
        String op = m.group(2);
        String rhsLit = m.group(3).trim();

        // String literal -> string compare
        if (rhsLit.length() >= 2 &&
                (rhsLit.charAt(0) == '"' || rhsLit.charAt(0) == '\'') &&
                rhsLit.charAt(rhsLit.length() - 1) == rhsLit.charAt(0)) {
            String s = rhsLit.substring(1, rhsLit.length() - 1);
            String l = lhs == null || lhs.isNull() ? null : lhs.asText();
            return compareStrings(l, s, op);
        }
        if ("true".equalsIgnoreCase(rhsLit) || "false".equalsIgnoreCase(rhsLit)) {
            boolean r = Boolean.parseBoolean(rhsLit);
            boolean l = lhs != null && lhs.asBoolean(false);
            return "==".equals(op) ? l == r : "!=".equals(op) ? l != r
                    : throwOp(op, "boolean");
        }
        if ("null".equalsIgnoreCase(rhsLit)) {
            boolean isNull = lhs == null || lhs.isNull();
            return "==".equals(op) ? isNull : "!=".equals(op) ? !isNull : throwOp(op, "null");
        }
        // Numeric
        double r = Double.parseDouble(rhsLit);
        double l = lhs == null || lhs.isNull() ? Double.NaN : lhs.asDouble();
        if (Double.isNaN(l)) return false;
        return switch (op) {
            case "==" -> l == r;
            case "!=" -> l != r;
            case ">"  -> l > r;
            case "<"  -> l < r;
            case ">=" -> l >= r;
            case "<=" -> l <= r;
            default   -> throwOp(op, "number");
        };
    }

    private static boolean compareStrings(String l, String r, String op) {
        return switch (op) {
            case "==" -> java.util.Objects.equals(l, r);
            case "!=" -> !java.util.Objects.equals(l, r);
            default   -> throw new IllegalArgumentException("op " + op + " not valid for strings");
        };
    }

    private static boolean throwOp(String op, String type) {
        throw new IllegalArgumentException("op " + op + " not valid for " + type);
    }

    private static JsonNode resolvePath(JsonNode root, String path) {
        JsonNode cur = root;
        for (String seg : path.split("\\.")) {
            if (cur == null || cur.isNull()) return null;
            cur = cur.get(seg);
        }
        return cur;
    }
}
