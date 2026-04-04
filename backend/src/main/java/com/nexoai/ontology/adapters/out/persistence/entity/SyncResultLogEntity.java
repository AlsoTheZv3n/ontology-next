package com.nexoai.ontology.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_result_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncResultLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "data_source_id", nullable = false)
    private UUID dataSourceId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "objects_synced")
    @Builder.Default
    private int objectsSynced = 0;

    @Column(name = "objects_created")
    @Builder.Default
    private int objectsCreated = 0;

    @Column(name = "objects_updated")
    @Builder.Default
    private int objectsUpdated = 0;

    @Column(name = "objects_failed")
    @Builder.Default
    private int objectsFailed = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
