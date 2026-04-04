package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "schema_migrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaMigrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "object_type_id", nullable = false)
    private UUID objectTypeId;

    @Column(name = "from_version", nullable = false)
    private int fromVersion;

    @Column(name = "to_version", nullable = false)
    private int toVersion;

    @Column(name = "migration_type", nullable = false, length = 50)
    private String migrationType;

    @Column(name = "source_property", length = 100)
    private String sourceProperty;

    @Column(name = "target_property", length = 100)
    private String targetProperty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transformation", columnDefinition = "jsonb")
    private String transformation;
}
