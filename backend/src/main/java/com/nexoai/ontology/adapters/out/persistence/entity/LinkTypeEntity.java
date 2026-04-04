package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "link_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "api_name", unique = true, nullable = false, length = 100)
    private String apiName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "source_object_type_id", nullable = false)
    private UUID sourceObjectTypeId;

    @Column(name = "target_object_type_id", nullable = false)
    private UUID targetObjectTypeId;

    @Column(name = "cardinality", nullable = false, length = 20)
    private String cardinality;

    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
