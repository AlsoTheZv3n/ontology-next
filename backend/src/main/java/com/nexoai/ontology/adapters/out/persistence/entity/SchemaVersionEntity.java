package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schema_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "object_type_id", nullable = false)
    private UUID objectTypeId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_snapshot", nullable = false, columnDefinition = "jsonb")
    private String schemaSnapshot;

    @Column(name = "change_summary")
    private String changeSummary;

    @Column(name = "is_breaking")
    @Builder.Default
    private boolean isBreaking = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;
}
