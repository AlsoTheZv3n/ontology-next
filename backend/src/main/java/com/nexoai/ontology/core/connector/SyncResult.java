package com.nexoai.ontology.core.connector;

import lombok.*;
import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class SyncResult {
    private final String status;
    private final int objectsCreated;
    private final int objectsUpdated;
    private final int objectsFailed;
    private final String errorMessage;
    private final Instant startedAt;
    private final Instant finishedAt;

    public int objectsSynced() {
        return objectsCreated + objectsUpdated;
    }

    public static SyncResult success(int created, int updated, int failed, Instant startedAt) {
        return SyncResult.builder()
                .status("SUCCESS")
                .objectsCreated(created).objectsUpdated(updated).objectsFailed(failed)
                .startedAt(startedAt).finishedAt(Instant.now()).build();
    }

    public static SyncResult failed(String errorMessage, Instant startedAt) {
        return SyncResult.builder()
                .status("FAILED").errorMessage(errorMessage)
                .startedAt(startedAt).finishedAt(Instant.now()).build();
    }
}
