package com.nexoai.ontology.adapters.in.graphql;

import com.nexoai.ontology.core.agent.AgentSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class AgentResolver {

    private final AgentSessionService agentSessionService;

    @MutationMapping
    public Map<String, Object> startAgentSession() {
        return agentSessionService.startSession();
    }

    @MutationMapping
    public Map<String, Object> agentChat(@Argument String sessionId, @Argument String message) {
        UUID sid = sessionId != null ? UUID.fromString(sessionId) : null;
        return agentSessionService.chat(sid, message);
    }

    @MutationMapping
    public Map<String, Object> confirmAgentAction(@Argument String sessionId,
                                                    @Argument String approvalId,
                                                    @Argument Boolean approved) {
        return agentSessionService.confirmAction(
                UUID.fromString(sessionId), UUID.fromString(approvalId), approved);
    }

    @QueryMapping
    public List<Map<String, Object>> getAgentAuditLog(@Argument Integer limit) {
        int lim = limit != null ? limit : 50;
        return agentSessionService.getAuditLog(null, lim);
    }
}
