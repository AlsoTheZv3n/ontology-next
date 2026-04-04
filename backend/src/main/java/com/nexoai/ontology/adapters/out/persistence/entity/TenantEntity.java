package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "api_name", unique = true, nullable = false, length = 100)
    private String apiName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(length = 50)
    @Builder.Default
    private String plan = "FREE";

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "max_object_types")
    @Builder.Default
    private int maxObjectTypes = 10;

    @Column(name = "max_objects")
    @Builder.Default
    private int maxObjects = 10000;

    @Column(name = "max_connectors")
    @Builder.Default
    private int maxConnectors = 3;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
