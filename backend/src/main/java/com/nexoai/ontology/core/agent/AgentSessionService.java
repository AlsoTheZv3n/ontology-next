package com.nexoai.ontology.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.*;
import com.nexoai.ontology.adapters.out.persistence.repository.*;
import com.nexoai.ontology.core.service.action.ActionEngine;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AgentSessionService {

    private final JpaAgentSessionRepository sessionRepository;
    private final JpaPendingApprovalRepository approvalRepository;
    private final JpaAgentAuditLogRepository auditLogRepository;
    private final OntologyAgentTools agentTools;
    private final ActionEngine actionEngine;
    private final ObjectMapper objectMapper;

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

        // Process the message using tool routing
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        String agentResponse;

        try {
            agentResponse = processMessage(userMessage, toolCalls);
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
