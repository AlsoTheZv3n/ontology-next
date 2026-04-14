package com.nexoai.ontology.core.entityresolution;

import com.nexoai.ontology.core.domain.object.OntologyObject;
import com.nexoai.ontology.core.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Async hook invoked after every object upsert. Runs entity resolution,
 * persists PENDING decisions for mid-confidence matches, and auto-merges
 * high-confidence duplicates.
 *
 * Thresholds:
 *  - confidence < 0.75         -> ignored (noise)
 *  - 0.75 <= confidence < 0.95 -> PENDING resolution_decision (human review)
 *  - confidence >= 0.95        -> AUTO_MERGED decision (no review)
 *
 * Auto-merge currently only records the decision; actual object-level merge
 * is a future step (requires merged_into_id on ontology_objects).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResolutionRunner {

    public static final double PENDING_THRESHOLD = 0.75;
    public static final double AUTO_MERGE_THRESHOLD = 0.95;
    public static final int MAX_CANDIDATES_PER_CHECK = 5;

    private final EntityResolutionEngine engine;
    private final ResolutionService resolutionService;
    private final MeterRegistry meterRegistry;

    /**
     * Run asynchronously so it doesn't slow down the upsert call path.
     * TenantContext is captured at call time but ThreadLocal doesn't propagate
     * to @Async threads by default — caller must pass tenantId explicitly.
     */
    @Async
    public void scheduleCheck(OntologyObject subject, UUID tenantId) {
        if (subject == null || tenantId == null) return;
        // Set tenant in the async thread so RLS-aware queries work
        TenantContext.setTenantId(tenantId);
        try {
            runCheck(subject, tenantId);
        } finally {
            TenantContext.clear();
        }
    }

    void runCheck(OntologyObject subject, UUID tenantId) {
        var candidates = engine.findDuplicates(subject, MAX_CANDIDATES_PER_CHECK);

        for (var c : candidates) {
            if (c.confidence() >= AUTO_MERGE_THRESHOLD) {
                resolutionService.markAutoMerged(
                        tenantId,
                        subject.getObjectTypeId(),
                        subject.getId(),
                        c.objectId(),
                        c.matchType(),
                        c.confidence());
                meterRegistry.counter("nexo.resolution.auto_merged").increment();
                log.info("Auto-merged candidate: subject={} peer={} conf={}",
                        subject.getId(), c.objectId(), c.confidence());
            } else if (c.confidence() >= PENDING_THRESHOLD) {
                resolutionService.createPending(
                        tenantId,
                        subject.getObjectTypeId(),
                        subject.getId(),
                        c.objectId(),
                        c.matchType(),
                        c.confidence(),
                        c.features());
                meterRegistry.counter("nexo.resolution.pending_created").increment();
                log.info("PENDING resolution decision: subject={} peer={} conf={}",
                        subject.getId(), c.objectId(), c.confidence());
            }
        }
    }
}
