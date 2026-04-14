package com.nexoai.ontology.core.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import com.nexoai.ontology.core.entityresolution.EntityResolutionEngine;
import com.nexoai.ontology.core.entityresolution.EntityResolutionEngine.Candidate;
import com.nexoai.ontology.core.entityresolution.ResolutionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bulk duplicate scan + merge workflow on top of the prod-05 detection engine.
 *
 * Detection reuses {@link EntityResolutionEngine} and persists via
 * {@link ResolutionService}, so there is one source of truth for resolution
 * decisions. What this class adds over prod-05:
 *   1) an O(N²) scan across every object of a given type (vs. upsert-time 1:N)
 *   2) an explicit merge operation that re-points object_links from loser to
 *      winner and deletes the loser row, while stamping the decision AUTO_MERGED.
 *
 * The scan is synchronous and suitable for ad-hoc / scheduled runs up to a few
 * thousand objects per type. Larger sets need blocking (prefix on a key field)
 * which is tracked as future work.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateDetectionService {

    private static final int MAX_CANDIDATES_PER_OBJECT = 5;

    private final EntityResolutionEngine engine;
    private final ResolutionService resolutionService;
    private final JpaOntologyObjectRepository objectRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MeterRegistry meterRegistry;

    public record ScanReport(UUID objectTypeId, int objectsScanned, int pairsFound) {}

    /**
     * Walk every object of the given type and record resolution_decisions for
     * pairs whose similarity crosses the EntityResolutionEngine threshold.
     * Returns a summary; the decisions themselves live in resolution_decisions.
     */
    @Transactional
    public ScanReport scanObjectType(UUID tenantId, UUID objectTypeId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        List<OntologyObjectEntity> peers = objectRepository.findAll().stream()
                .filter(e -> objectTypeId.equals(e.getObjectTypeId()))
                .toList();

        int pairsFound = 0;
        for (OntologyObjectEntity entity : peers) {
            OntologyObject domain = toDomain(entity, objectTypeId);
            if (domain == null) continue;

            List<Candidate> candidates = engine.findDuplicates(domain, MAX_CANDIDATES_PER_OBJECT);
            for (Candidate c : candidates) {
                // Canonicalize ordering so the unique-pair constraint prevents double-insert.
                UUID lo = entity.getId().compareTo(c.objectId()) < 0 ? entity.getId() : c.objectId();
                UUID hi = entity.getId().compareTo(c.objectId()) < 0 ? c.objectId() : entity.getId();
                resolutionService.createPending(tenantId, objectTypeId, lo, hi,
                        c.matchType(), c.confidence(), c.features());
                pairsFound++;
            }
        }
        sample.stop(meterRegistry.timer("nexo.dedup.scan.duration",
                "objectType", objectTypeId.toString()));
        meterRegistry.counter("nexo.dedup.candidates.total",
                "tenant", tenantId.toString()).increment(pairsFound);
        log.info("Dedup scan: type={} scanned={} pairsFound={}",
                objectTypeId, peers.size(), pairsFound);
        return new ScanReport(objectTypeId, peers.size(), pairsFound);
    }

    /**
     * Confirm a duplicate candidate by merging loser → winner:
     *   - Re-point every object_links row that references the loser as source/target.
     *     ON CONFLICT the loser-links are silently dropped (already connected via winner).
     *   - Delete the loser row.
     *   - Stamp the resolution_decision AUTO_MERGED.
     *
     * Hard delete is chosen intentionally: without a merged_into column in
     * ontology_objects, soft delete would leave orphan rows that look live to
     * consumers. Adding soft-delete support is a schema change tracked separately.
     */
    @Transactional
    public void confirmMerge(UUID decisionId, UUID winnerId, String resolvedBy) {
        var decision = jdbc.queryForMap("""
                SELECT candidate_a_id, candidate_b_id, tenant_id, object_type_id, match_type, confidence
                  FROM resolution_decisions
                 WHERE id = ?::uuid
                """, decisionId.toString());

        UUID a = UUID.fromString(decision.get("candidate_a_id").toString());
        UUID b = UUID.fromString(decision.get("candidate_b_id").toString());
        if (!winnerId.equals(a) && !winnerId.equals(b)) {
            throw new IllegalArgumentException("winnerId must match one of the candidates");
        }
        UUID loserId = winnerId.equals(a) ? b : a;

        jdbc.update("""
                UPDATE object_links SET source_id = ?::uuid
                 WHERE source_id = ?::uuid
                   AND NOT EXISTS (
                        SELECT 1 FROM object_links existing
                         WHERE existing.source_id = ?::uuid
                           AND existing.target_id = object_links.target_id
                           AND existing.link_type_id = object_links.link_type_id)
                """,
                winnerId.toString(), loserId.toString(), winnerId.toString());
        jdbc.update("""
                UPDATE object_links SET target_id = ?::uuid
                 WHERE target_id = ?::uuid
                   AND NOT EXISTS (
                        SELECT 1 FROM object_links existing
                         WHERE existing.target_id = ?::uuid
                           AND existing.source_id = object_links.source_id
                           AND existing.link_type_id = object_links.link_type_id)
                """,
                winnerId.toString(), loserId.toString(), winnerId.toString());
        // Delete any links that could not be re-pointed (duplicate winner link already existed).
        jdbc.update("DELETE FROM object_links WHERE source_id = ?::uuid OR target_id = ?::uuid",
                loserId.toString(), loserId.toString());
        // Remove the loser object itself.
        objectRepository.deleteById(loserId);

        jdbc.update("""
                UPDATE resolution_decisions
                   SET status = 'AUTO_MERGED', resolved_at = NOW(), resolved_by = ?
                 WHERE id = ?::uuid
                """, resolvedBy, decisionId.toString());
        log.info("Merged loser={} into winner={} (decision={})", loserId, winnerId, decisionId);
    }

    private OntologyObject toDomain(OntologyObjectEntity e, UUID objectTypeId) {
        JsonNode props;
        try {
            props = mapper.readTree(e.getProperties() == null ? "{}" : e.getProperties());
        } catch (Exception ex) { return null; }
        return OntologyObject.builder()
                .id(e.getId())
                .objectTypeId(objectTypeId)
                .properties(props)
                .build();
    }

    // Expose list of candidates for ad-hoc tooling / tests.
    public List<Candidate> findDuplicatesFor(OntologyObject subject) {
        List<Candidate> result = new ArrayList<>();
        result.addAll(engine.findDuplicates(subject, MAX_CANDIDATES_PER_OBJECT));
        return result;
    }
}
