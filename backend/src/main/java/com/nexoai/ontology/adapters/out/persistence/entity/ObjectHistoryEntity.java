package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "object_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObjectHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private int schemaVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", nullable = false, columnDefinition = "jsonb")
    private String properties;

    @Column(name = "tx_from", nullable = false)
    @Builder.Default
    private Instant txFrom = Instant.now();

    @Column(name = "tx_to")
    private Instant txTo;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private Instant validFrom = Instant.now();

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "change_source", length = 100)
    private String changeSource;

    @Column(name = "changed_by")
    private String changedBy;
}
