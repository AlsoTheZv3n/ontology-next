package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentAuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_message", nullable = false)
    private String userMessage;

    @Column(name = "agent_response")
    private String agentResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    @Builder.Default
    private String toolCalls = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions_executed", columnDefinition = "jsonb")
    @Builder.Default
    private String actionsExecuted = "[]";

    @CreationTimestamp
    @Column(name = "performed_at", updatable = false)
    private Instant performedAt;
}
