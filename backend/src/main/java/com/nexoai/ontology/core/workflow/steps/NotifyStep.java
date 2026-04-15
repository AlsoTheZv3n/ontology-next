package com.nexoai.ontology.core.workflow.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.notification.NotificationChannel;
import com.nexoai.ontology.core.notification.NotificationDispatcher;
import com.nexoai.ontology.core.tenant.TenantContext;
import com.nexoai.ontology.core.workflow.WorkflowContext;
import com.nexoai.ontology.core.workflow.WorkflowStepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Workflow step that fans a notification out to one or more channels via
 * {@link NotificationDispatcher} (Slack / Teams / InApp etc., from prod-09).
 *
 * Step config schema:
 *   {
 *     "type":      "NOTIFY",
 *     "title":     "Sync failed",
 *     "body":      "...",
 *     "recipient": "alerts@nexo.ai",
 *     "channels":  ["SLACK", "TEAMS"],
 *     "config":    { "slack": { "webhookUrl": "..." } }
 *   }
 *
 * Returns OK if at least one channel succeeded, FAILED if all failed.
 * Per-channel outcomes go into the step output for run-history visibility.
 */
@Component
@RequiredArgsConstructor
public class NotifyStep implements WorkflowStepExecutor {

    private final NotificationDispatcher dispatcher;
    private final ObjectMapper mapper;

    @Override public String stepType() { return "NOTIFY"; }

    @Override
    public StepResult execute(JsonNode cfg, WorkflowContext ctx) {
        JsonNode channelsNode = cfg.path("channels");
        if (!channelsNode.isArray() || channelsNode.isEmpty()) {
            return StepResult.fail("missing channels[]");
        }
        List<String> channels = new ArrayList<>(channelsNode.size());
        channelsNode.forEach(n -> channels.add(n.asText()));

        UUID tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId == null) {
            tenantId = new UUID(0L, 0L); // workflow may run in system context
        }

        var req = new NotificationChannel.NotificationRequest(
                UUID.randomUUID(),
                tenantId,
                "workflow",
                cfg.path("recipient").asText(""),
                cfg.path("title").asText(""),
                cfg.path("body").asText(""),
                ctx.root());

        var outcomes = dispatcher.dispatch(req, channels, cfg.path("config"));
        boolean anySuccess = outcomes.stream().anyMatch(NotificationDispatcher.DeliveryOutcome::success);

        ObjectNode out = mapper.createObjectNode();
        out.set("outcomes", mapper.valueToTree(outcomes));
        out.put("anySuccess", anySuccess);

        return anySuccess
                ? StepResult.ok(out)
                : new StepResult("FAILED", out, "all channels failed");
    }
}
