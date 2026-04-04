package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_approvals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PendingApprovalEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "object_id")
    private UUID objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String params;

    @Column(name = "human_readable_summary")
    private String humanReadableSummary;

    @Column(name = "agent_reasoning")
    private String agentReasoning;

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
