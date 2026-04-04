package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "property_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_type_id", nullable = false)
    private ObjectTypeEntity objectType;

    @Column(name = "api_name", nullable = false, length = 100)
    private String apiName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(name = "is_primary_key")
    @Builder.Default
    private boolean isPrimaryKey = false;

    @Column(name = "is_required")
    @Builder.Default
    private boolean isRequired = false;

    @Column(name = "is_indexed")
    @Builder.Default
    private boolean isIndexed = false;

    @Column(name = "default_value")
    private String defaultValue;

    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
