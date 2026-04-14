package com.nexoai.ontology.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.*;
import com.nexoai.ontology.adapters.out.persistence.repository.*;
import com.nexoai.ontology.config.websocket.WebSocketPublisher;
import com.nexoai.ontology.core.agent.llm.*;
import com.nexoai.ontology.core.cdc.ObjectChangeEvent;
import com.nexoai.ontology.core.service.action.ActionEngine;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@Transactional
public class AgentSessionService {

    private final JpaAgentSessionRepository sessionRepository;
    private final JpaPendingApprovalRepository approvalRepository;
    private final JpaAgentAuditLogRepository auditLogRepository;
    private final OntologyAgentTools agentTools;
    private final ActionEngine actionEngine;
    private final ObjectMapper objectMapper;
    private final LlmProvider llmProvider;
    private final WebSocketPublisher wsPublisher;

    private static final String SYSTEM_PROMPT = """
            You are the NEXO Ontology Agent. You help users explore and manage their ontology data.
            You can search objects, traverse relationships, and compute aggregations.
            Respond in the same language the user writes in. Be concise and helpful.
            """;

    public AgentSessionService(
            JpaAgentSessionRepository sessionRepository,
            JpaPendingApprovalRepository approvalRepository,
            JpaAgentAuditLogRepository auditLogRepository,
            OntologyAgentTools agentTools,
            ActionEngine actionEngine,
            ObjectMapper objectMapper,
            Optional<LlmProvider> llmProvider,
            WebSocketPublisher wsPublisher) {
        this.sessionRepository = sessionRepository;
        this.approvalRepository = approvalRepository;
        this.auditLogRepository = auditLogRepository;
        this.agentTools = agentTools;
        this.actionEngine = actionEngine;
        this.objectMapper = objectMapper;
        this.llmProvider = llmProvider.orElse(null);
        this.wsPublisher = wsPublisher;
    }

    public Map<String, Object> startSession() {
        var session = sessionRepository.save(AgentSessionEntity.builder()
                .tenantId(TenantContext.getTenantId())
                .createdBy(TenantContext.getCurrentUser())
                .build());
        return Map.of("sessionId", session.getId().toString(), "createdAt", session.getCreatedAt());
    }

    public Map<String, Object> chat(UUID sessionId, String userMessage) {
        // Get or create session
        AgentSessionEntity session;
        if (sessionId != null) {
            session = sessionRepository.findById(sessionId).orElse(null);
        } else {
            session = null;
        }
        if (session == null) {
            session = sessionRepository.save(AgentSessionEntity.builder()
                    .tenantId(TenantContext.getTenantId())
                    .createdBy(TenantContext.getCurrentUser())
                    .build());
        }

        // Load conversation history from DB (Fix 10e)
        List<LlmMessage> conversationHistory = loadHistory(session.getId());

        // Process the message using LLM or keyword routing
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        String agentResponse;

        try {
            agentResponse = processMessageWithLlm(userMessage, toolCalls, conversationHistory);
        } catch (Exception e) {
            log.error("Agent processing failed: {}", e.getMessage());
            agentResponse = "Entschuldigung, bei der Verarbeitung ist ein Fehler aufgetreten: " + e.getMessage();
        }

        // Update session
        session.setLastActiveAt(Instant.now());
        sessionRepository.save(session);

        // Log to audit
        auditLogRepository.save(AgentAuditLogEntity.builder()
                .sessionId(session.getId())
                .tenantId(TenantContext.getTenantId())
                .userMessage(userMessage)
                .agentResponse(agentResponse)
                .toolCalls(toJson(toolCalls))
                .build());

        // Check for pending approvals
        var pendingApprovals = approvalRepository.findBySessionIdAndStatus(session.getId(), "PENDING");

        // Fix 10f: broadcast when there are pending approvals
        if (!pendingApprovals.isEmpty()) {
            wsPublisher.broadcastChange(ObjectChangeEvent.builder()
                    .operation("PENDING_APPROVAL")
                    .timestamp(Instant.now())
                    .build());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", agentResponse);
        response.put("sessionId", session.getId().toString());
        response.put("toolCalls", toolCalls);
        response.put("data", null);
        response.put("pendingApprovals", pendingApprovals.stream().map(this::approvalToMap).toList());
        response.put("requiresConfirmation", !pendingApprovals.isEmpty());
        return response;
    }

    public Map<String, Object> confirmAction(UUID sessionId, UUID approvalId, boolean approved) {
        var approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new RuntimeException("Approval not found"));

        if (approved) {
            approval.setStatus("APPROVED");
            approval.setResolvedBy(TenantContext.getCurrentUser());
            approval.setResolvedAt(Instant.now());
            approvalRepository.save(approval);

            // Execute the action
            var paramsNode = parseJson(approval.getParams());
            actionEngine.executeAction(approval.getActionType(), approval.getObjectId(),
                    paramsNode, "agent-approved-by:" + TenantContext.getCurrentUser());

            return Map.of("message", "Action ausgefuehrt: " + approval.getActionType(),
                    "sessionId", sessionId.toString(),
                    "toolCalls", List.of(), "pendingApprovals", List.of(),
                    "requiresConfirmation", false);
        } else {
            approval.setStatus("REJECTED");
            approval.setResolvedBy(TenantContext.getCurrentUser());
            approval.setResolvedAt(Instant.now());
            approvalRepository.save(approval);

            return Map.of("message", "Action abgebrochen.",
                    "sessionId", sessionId.toString(),
                    "toolCalls", List.of(), "pendingApprovals", List.of(),
                    "requiresConfirmation", false);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAuditLog(UUID tenantId, int limit) {
        UUID tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        return auditLogRepository.findByTenantIdOrderByPerformedAtDesc(tid, PageRequest.of(0, limit))
                .stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sessionId", e.getSessionId().toString());
                    m.put("userMessage", e.getUserMessage());
                    m.put("agentResponse", e.getAgentResponse());
                    m.put("toolCalls", parseJson(e.getToolCalls()));
                    m.put("actionsExecuted", parseJson(e.getActionsExecuted()));
                    m.put("performedAt", e.getPerformedAt());
                    return m;
                }).toList();
    }

    /**
     * Load conversation history from the audit log for context (Fix 10e).
     */
    private List<LlmMessage> loadHistory(UUID sessionId) {
        List<LlmMessage> history = new ArrayList<>();
        var auditEntries = auditLogRepository.findBySessionIdOrderByPerformedAtDesc(sessionId);
        // Reverse to get chronological order, take last 20 turns max
        var entries = auditEntries.subList(0, Math.min(auditEntries.size(), 20));
        Collections.reverse(entries);
        for (var entry : entries) {
            if (entry.getUserMessage() != null) {
                history.add(LlmMessage.user(entry.getUserMessage()));
            }
            if (entry.getAgentResponse() != null) {
                history.add(LlmMessage.assistant(entry.getAgentResponse()));
            }
        }
        return history;
    }

    /**
     * Agent loop with tool-calling.
     * LLM -> if tool_use -> execute tool -> feed result back -> repeat until END_TURN.
     * Falls back to keyword routing if no LLM is configured.
     */
    private static final int MAX_AGENT_ITERATIONS = 5;

    private String processMessageWithLlm(String userMessage, List<Map<String, Object>> toolCalls,
                                          List<LlmMessage> conversationHistory) {
        if (llmProvider == null || !llmProvider.isAvailable()) {
            // No LLM configured → keyword routing fallback
            return processMessage(userMessage, toolCalls);
        }

        List<LlmMessage> messages = new ArrayList<>(conversationHistory);
        messages.add(LlmMessage.user(userMessage));
        List<LlmToolDefinition> tools = agentTools.toolDefinitions();

        int totalInputTokens = 0;
        int totalOutputTokens = 0;

        for (int i = 0; i < MAX_AGENT_ITERATIONS; i++) {
            LlmResponse response = llmProvider.chatWithTools(SYSTEM_PROMPT, messages, tools);
            totalInputTokens += response.inputTokens();
            totalOutputTokens += response.outputTokens();

            // End of turn: LLM has final text answer
            if (!response.hasToolCalls()) {
                log.info("Agent finished after {} iterations (tokens: in={}, out={})",
                        i + 1, totalInputTokens, totalOutputTokens);
                return response.content() != null ? response.content() : "";
            }

            // LLM wants to call tools — execute them and feed results back
            // First, add the assistant's "thinking" message (with tool_use blocks)
            messages.add(LlmMessage.assistant(response.content() != null ? response.content() : ""));

            for (LlmResponse.LlmToolCall call : response.toolCalls()) {
                long t0 = System.currentTimeMillis();
                String resultJson;
                try {
                    Object result = agentTools.executeTool(call.name(), call.arguments());
                    resultJson = objectMapper.writeValueAsString(result);
                    toolCalls.add(Map.of(
                            "tool", call.name(),
                            "input", call.arguments(),
                            "resultSummary", summarize(result),
                            "duration", System.currentTimeMillis() - t0
                    ));
                } catch (Exception e) {
                    log.error("Tool {} execution failed: {}", call.name(), e.getMessage());
                    resultJson = "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
                    toolCalls.add(Map.of(
                            "tool", call.name(),
                            "input", call.arguments(),
                            "resultSummary", "ERROR: " + e.getMessage(),
                            "duration", System.currentTimeMillis() - t0
                    ));
                }
                // Feed tool result back to LLM
                messages.add(LlmMessage.toolResult(call.id(), call.name(), resultJson));
            }
        }

        log.warn("Agent loop hit MAX_AGENT_ITERATIONS={} without END_TURN", MAX_AGENT_ITERATIONS);
        return "Agent reached iteration limit without final answer. Please rephrase your question.";
    }

    private String summarize(Object result) {
        if (result == null) return "null";
        String s = String.valueOf(result);
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /**
     * Simple keyword-based tool routing.
     * PLACEHOLDER: Replace with actual LLM (Spring AI / Anthropic Claude / OpenAI) for production.
     */
    private String processMessage(String userMessage, List<Map<String, Object>> toolCalls) {
        String lower = userMessage.toLowerCase();

        // Route 1: Schema queries
        if (lower.contains("schema") || lower.contains("welche typen") || lower.contains("object types")
                || lower.contains("was gibt es")) {
            var schema = agentTools.getOntologySchema();
            toolCalls.add(Map.of("tool", "getOntologySchema", "input", Map.of(), "resultSummary", schema.toString(), "duration", 10));
            @SuppressWarnings("unchecked")
            var types = (List<Map<String, Object>>) schema.get("objectTypes");
            return "Das aktuelle Ontology-Schema enthaelt %d Object Types: %s".formatted(
                    types.size(),
                    types.stream().map(t -> t.get("apiName").toString()).reduce((a, b) -> a + ", " + b).orElse("keine"));
        }

        // Route 2: Search
        if (lower.contains("suche") || lower.contains("finde") || lower.contains("zeig") || lower.contains("search")) {
            String objectType = extractObjectType(lower);
            if (objectType != null) {
                var result = agentTools.searchObjects(objectType, userMessage, 10);
                toolCalls.add(Map.of("tool", "searchObjects", "input", Map.of("objectType", objectType, "query", userMessage), "resultSummary", "Found " + result.get("count"), "duration", 50));
                return "Ich habe %s Objekte vom Typ '%s' gefunden.".formatted(result.get("count"), objectType);
            }
        }

        // Route 3: Count/Aggregate
        if (lower.contains("wie viele") || lower.contains("anzahl") || lower.contains("count") || lower.contains("summe") || lower.contains("durchschnitt")) {
            String objectType = extractObjectType(lower);
            String op = lower.contains("summe") || lower.contains("sum") ? "SUM"
                    : lower.contains("durchschnitt") || lower.contains("avg") ? "AVG" : "COUNT";
            if (objectType != null) {
                var result = agentTools.aggregateObjects(objectType, op, "revenue");
                toolCalls.add(Map.of("tool", "aggregateObjects", "input", Map.of("objectType", objectType, "operation", op), "resultSummary", result.toString(), "duration", 30));
                return "Ergebnis der %s-Aggregation fuer '%s': %s".formatted(op, objectType, result.get("result"));
            }
        }

        // Default: return schema info
        var schema = agentTools.getOntologySchema();
        toolCalls.add(Map.of("tool", "getOntologySchema", "input", Map.of(), "resultSummary", "Schema loaded", "duration", 5));
        return "Ich bin der NEXO Ontology Agent. Ich kann Daten suchen, Beziehungen traversieren und Aggregationen berechnen. " +
                "Aktuell gibt es %s Object Types im System. Was moechtest du wissen?".formatted(schema.get("totalTypes"));
    }

    private String extractObjectType(String message) {
        var types = agentTools.getOntologySchema();
        @SuppressWarnings("unchecked")
        var objectTypes = (List<Map<String, Object>>) types.get("objectTypes");
        for (var ot : objectTypes) {
            String name = ot.get("apiName").toString().toLowerCase();
            if (message.contains(name)) return ot.get("apiName").toString();
        }
        return objectTypes.isEmpty() ? null : objectTypes.get(0).get("apiName").toString();
    }

    private Map<String, Object> approvalToMap(PendingApprovalEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("actionType", e.getActionType());
        m.put("objectId", e.getObjectId() != null ? e.getObjectId().toString() : null);
        m.put("objectSummary", "");
        m.put("params", parseJson(e.getParams()));
        m.put("humanReadableSummary", e.getHumanReadableSummary() != null ? e.getHumanReadableSummary() : "");
        m.put("agentReasoning", e.getAgentReasoning() != null ? e.getAgentReasoning() : "");
        return m;
    }

    private com.fasterxml.jackson.databind.JsonNode parseJson(String json) {
        if (json == null) return objectMapper.createArrayNode();
        try { return objectMapper.readTree(json); }
        catch (Exception e) { return objectMapper.createArrayNode(); }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }
}
