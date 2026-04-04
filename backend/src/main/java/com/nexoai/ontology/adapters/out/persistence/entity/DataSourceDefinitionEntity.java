package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_source_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataSourceDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "api_name", unique = true, nullable = false, length = 100)
    private String apiName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "connector_type", nullable = false, length = 20)
    private String connectorType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private String config;

    @Column(name = "object_type_id")
    private UUID objectTypeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping", columnDefinition = "jsonb")
    private String columnMapping;

    @Column(name = "sync_interval_cron", length = 100)
    @Builder.Default
    private String syncIntervalCron = "0 */15 * * * *";

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "source_table")
    private String sourceTable;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
