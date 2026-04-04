package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "action_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "api_name", unique = true, nullable = false, length = 100)
    private String apiName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "target_object_type_id")
    private UUID targetObjectTypeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules", columnDefinition = "jsonb")
    private String validationRules;

    @Column(name = "requires_approval")
    @Builder.Default
    private boolean requiresApproval = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "side_effects", columnDefinition = "jsonb")
    private String sideEffects;

    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
