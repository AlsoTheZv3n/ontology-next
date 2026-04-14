package com.nexoai.ontology.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.AgentSessionEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaAgentAuditLogRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaAgentSessionRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaPendingApprovalRepository;
import com.nexoai.ontology.config.websocket.WebSocketPublisher;
import com.nexoai.ontology.core.agent.llm.LlmProvider;
import com.nexoai.ontology.core.agent.llm.LlmResponse;
import com.nexoai.ontology.core.agent.llm.LlmToolDefinition;
import com.nexoai.ontology.core.service.action.ActionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Tests that the agent loop correctly:
 * 1. Calls LLM with tools
 * 2. Executes tool when LLM returns tool_use
 * 3. Feeds result back to LLM
 * 4. Returns final text answer on END_TURN
 */
@ExtendWith(MockitoExtension.class)
class AgentLoopTest {

    @Mock private JpaAgentSessionRepository sessionRepository;
    @Mock private JpaPendingApprovalRepository approvalRepository;
    @Mock private JpaAgentAuditLogRepository auditLogRepository;
    @Mock private OntologyAgentTools agentTools;
    @Mock private ActionEngine actionEngine;
    @Mock private LlmProvider llmProvider;
    @Mock private WebSocketPublisher wsPublisher;

    private AgentSessionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AgentSessionService(
                sessionRepository, approvalRepository, auditLogRepository,
                agentTools, actionEngine, objectMapper,
                Optional.of(llmProvider), wsPublisher);
    }

    @Test
    void agent_executes_tool_when_llm_requests_it_and_returns_final_answer() throws Exception {
        // Setup: session persists
        AgentSessionEntity session = AgentSessionEntity.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .build();
        when(sessionRepository.save(any())).thenReturn(session);
        when(auditLogRepository.findBySessionIdOrderByPerformedAtDesc(any()))
                .thenReturn(List.of()); // empty history
        when(approvalRepository.findBySessionIdAndStatus(any(), any()))
                .thenReturn(List.of());

        // LLM is available
        when(llmProvider.isAvailable()).thenReturn(true);

        // Tool definitions returned by agent
        when(agentTools.toolDefinitions()).thenReturn(List.of(
                new LlmToolDefinition("aggregateObjects", "Count objects",
                        Map.of("type", "object"))
        ));

        // Tool execution returns 42
        when(agentTools.executeTool(eq("aggregateObjects"), any()))
                .thenReturn(Map.of("result", 42, "operation", "COUNT"));

        // LLM first wants to call tool, then gives final answer
        LlmResponse firstResponse = new LlmResponse(
                "", // no text yet
                List.of(new LlmResponse.LlmToolCall(
                        "tool_call_1", "aggregateObjects",
                        Map.of("objectType", "Customer", "operation", "COUNT"))),
                "tool_use", 50, 20);

        LlmResponse finalResponse = new LlmResponse(
                "You have 42 Customers in the system.",
                List.of(),
                "end_turn", 80, 15);

        when(llmProvider.chatWithTools(any(), anyList(), anyList()))
                .thenReturn(firstResponse)
                .thenReturn(finalResponse);

        // Execute
        Map<String, Object> result = service.chat(null, "how many customers?");

        // Verify final answer
        assertThat(result.get("message")).isEqualTo("You have 42 Customers in the system.");

        // Verify tool was actually executed
        verify(agentTools).executeTool(eq("aggregateObjects"), any());

        // Verify two LLM calls (first for tool_use, second for final answer)
        verify(llmProvider, times(2)).chatWithTools(any(), anyList(), anyList());

        // Verify toolCalls list in response
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) result.get("toolCalls");
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0).get("tool")).isEqualTo("aggregateObjects");
    }

    @Test
    void agent_returns_final_answer_immediately_when_no_tools_needed() throws Exception {
        AgentSessionEntity session = AgentSessionEntity.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .build();
        when(sessionRepository.save(any())).thenReturn(session);
        when(auditLogRepository.findBySessionIdOrderByPerformedAtDesc(any()))
                .thenReturn(List.of());
        when(approvalRepository.findBySessionIdAndStatus(any(), any()))
                .thenReturn(List.of());

        when(llmProvider.isAvailable()).thenReturn(true);
        when(agentTools.toolDefinitions()).thenReturn(List.of());

        LlmResponse response = new LlmResponse(
                "Hello! I am the NEXO agent, how can I help?",
                List.of(),
                "end_turn", 20, 15);
        when(llmProvider.chatWithTools(any(), anyList(), anyList())).thenReturn(response);

        Map<String, Object> result = service.chat(null, "hi");

        assertThat(result.get("message")).isEqualTo("Hello! I am the NEXO agent, how can I help?");
        verify(agentTools, never()).executeTool(any(), any());
        verify(llmProvider, times(1)).chatWithTools(any(), anyList(), anyList());
    }

    @Test
    void agent_stops_at_iteration_limit_if_llm_keeps_requesting_tools() throws Exception {
        AgentSessionEntity session = AgentSessionEntity.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .build();
        when(sessionRepository.save(any())).thenReturn(session);
        when(auditLogRepository.findBySessionIdOrderByPerformedAtDesc(any()))
                .thenReturn(List.of());
        when(approvalRepository.findBySessionIdAndStatus(any(), any()))
                .thenReturn(List.of());

        when(llmProvider.isAvailable()).thenReturn(true);
        when(agentTools.toolDefinitions()).thenReturn(List.of());
        when(agentTools.executeTool(any(), any())).thenReturn(Map.of("ok", true));

        // LLM always requests tools — never returns END_TURN
        LlmResponse alwaysTool = new LlmResponse(
                "",
                List.of(new LlmResponse.LlmToolCall("t1", "foo", Map.of())),
                "tool_use", 10, 10);
        when(llmProvider.chatWithTools(any(), anyList(), anyList())).thenReturn(alwaysTool);

        Map<String, Object> result = service.chat(null, "question");

        // Should stop at iteration limit
        assertThat((String) result.get("message")).contains("iteration limit");
        verify(llmProvider, times(5)).chatWithTools(any(), anyList(), anyList());
    }

    @Test
    void agent_falls_back_to_keyword_routing_when_llm_not_available() throws Exception {
        AgentSessionEntity session = AgentSessionEntity.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .build();
        when(sessionRepository.save(any())).thenReturn(session);
        when(auditLogRepository.findBySessionIdOrderByPerformedAtDesc(any()))
                .thenReturn(List.of());
        when(approvalRepository.findBySessionIdAndStatus(any(), any()))
                .thenReturn(List.of());

        when(llmProvider.isAvailable()).thenReturn(false);
        when(agentTools.getOntologySchema()).thenReturn(Map.of(
                "objectTypes", List.of(Map.of("apiName", "Customer")),
                "totalTypes", 1));

        Map<String, Object> result = service.chat(null, "welche object types gibt es?");

        // Fallback should use keyword routing
        assertThat((String) result.get("message")).contains("Customer");
        // LLM should NOT have been called
        verify(llmProvider, never()).chatWithTools(any(), any(), any());
    }
}
