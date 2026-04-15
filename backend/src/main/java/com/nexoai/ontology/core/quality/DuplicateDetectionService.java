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

import org.apache.commons.codec.language.Soundex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final Soundex SOUNDEX = new Soundex();

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

        // Blocking: bucket peers by a coarse fingerprint (email-prefix or
        // Soundex of name). Objects in different blocks can't be duplicates,
        // so we skip those comparisons entirely. This drops the dominant
        // O(N²) cost to roughly O(Σ bᵢ²) — for evenly-distributed Customer
        // tables that's typically O(N²/k) where k = number of distinct keys.
        Map<String, List<OntologyObjectEntity>> blocks = bucketize(peers);

        int pairsFound = 0;
        int comparisons = 0;
        for (List<OntologyObjectEntity> block : blocks.values()) {
            if (block.size() < 2) continue;  // singletons can't have duplicates
            for (OntologyObjectEntity entity : block) {
                OntologyObject domain = toDomain(entity, objectTypeId);
                if (domain == null) continue;
                comparisons += block.size() - 1;

                List<Candidate> candidates = engine.findDuplicatesIn(
                        domain, block, MAX_CANDIDATES_PER_OBJECT);
                for (Candidate c : candidates) {
                    UUID lo = entity.getId().compareTo(c.objectId()) < 0
                            ? entity.getId() : c.objectId();
                    UUID hi = entity.getId().compareTo(c.objectId()) < 0
                            ? c.objectId() : entity.getId();
                    resolutionService.createPending(tenantId, objectTypeId, lo, hi,
                            c.matchType(), c.confidence(), c.features());
                    pairsFound++;
                }
            }
        }
        sample.stop(meterRegistry.timer("nexo.dedup.scan.duration",
                "objectType", objectTypeId.toString()));
        meterRegistry.counter("nexo.dedup.candidates.total",
                "tenant", tenantId.toString()).increment(pairsFound);
        log.info("Dedup scan: type={} scanned={} blocks={} comparisons={} pairsFound={}",
                objectTypeId, peers.size(), blocks.size(), comparisons, pairsFound);
        return new ScanReport(objectTypeId, peers.size(), pairsFound);
    }

    /**
     * Group entities by a coarse block key. Objects with different keys cannot
     * possibly be duplicates and are not compared.
     *
     * Block key (in order of preference):
     *   1. first 3 chars of email local-part (before @)
     *   2. Soundex of name / displayName / company_name
     *   3. "_none" — entities without either land in one big block, paying
     *      the full O(B²) cost. Acceptable as long as it's a small slice.
     */
    Map<String, List<OntologyObjectEntity>> bucketize(List<OntologyObjectEntity> peers) {
        Map<String, List<OntologyObjectEntity>> blocks = new HashMap<>();
        for (OntologyObjectEntity e : peers) {
            String key = blockKey(e);
            blocks.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        return blocks;
    }

    private String blockKey(OntologyObjectEntity entity) {
        try {
            JsonNode props = mapper.readTree(
                    entity.getProperties() == null ? "{}" : entity.getProperties());
            String email = props.path("email").asText("");
            if (!email.isBlank() && email.contains("@")) {
                String local = email.substring(0, email.indexOf('@'))
                        .toLowerCase(Locale.ROOT);
                return "email:" + local.substring(0, Math.min(3, local.length()));
            }
            for (String nameField : new String[]{"name", "displayName", "company_name"}) {
                String name = props.path(nameField).asText("");
                if (!name.isBlank()) {
                    return "name:" + soundexSafe(name);
                }
            }
            return "_none";
        } catch (Exception e) {
            return "_none";
        }
    }

    private static String soundexSafe(String input) {
        try {
            return SOUNDEX.encode(input);
        } catch (IllegalArgumentException e) {
            // Soundex throws on non-ASCII first chars; fall back to a coarse hash.
            return "x" + Math.abs(input.hashCode() % 1000);
        }
    }

    /**
     * Confirm a duplicate candidate by merging loser → winner:
     *   - Re-point every object_links row that references the loser as source/target.
     *     ON CONFLICT the loser-links are silently dropped (already connected via winner).
     *   - Soft-delete the loser row: set deleted_at + merged_into + merged_at + merged_by.
     *     The row stays in the table so external references (URLs, cached IDs,
     *     audit logs) can still resolve to the winner via the merged_into redirect,
     *     and an operator can call {@link #unmerge} to restore it.
     *   - Stamp the resolution_decision AUTO_MERGED.
     *
     * Soft-delete was introduced in V29 specifically to make this reversible —
     * the previous hard-delete implementation was flagged as red-05 in the
     * audit because a wrong merge (operator vertauschte winner/loser) had no
     * recovery path.
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

        // Soft-delete the loser. Active reads filter deleted_at IS NULL; merge
        // redirects use merged_into (see OntologyObjectService.findEffective).
        jdbc.update("""
                UPDATE ontology_objects
                   SET deleted_at = NOW(),
                       merged_into = ?::uuid,
                       merged_at = NOW(),
                       merged_by = ?
                 WHERE id = ?::uuid
                """,
                winnerId.toString(), resolvedBy, loserId.toString());

        jdbc.update("""
                UPDATE resolution_decisions
                   SET status = 'AUTO_MERGED', resolved_at = NOW(), resolved_by = ?
                 WHERE id = ?::uuid
                """, resolvedBy, decisionId.toString());
        log.info("Merged loser={} into winner={} (decision={})", loserId, winnerId, decisionId);
    }

    /**
     * Revert a confirmed merge. Clears deleted_at / merged_into on the loser
     * and flips the resolution_decision back to REJECTED so the pair stays
     * visible in the review queue but won't auto-re-merge.
     *
     * <p>Does NOT try to reconstruct the link graph that was re-pointed during
     * {@link #confirmMerge} — restoring those would require a separate change
     * log. For most use cases (wrong-direction merge, quick manual undo) the
     * winner already holds the right links and unmerging gives the loser back
     * as a standalone object to investigate.
     */
    @Transactional
    public void unmerge(UUID decisionId, String resolvedBy) {
        var decision = jdbc.queryForMap("""
                SELECT candidate_a_id, candidate_b_id
                  FROM resolution_decisions
                 WHERE id = ?::uuid
                """, decisionId.toString());
        UUID a = UUID.fromString(decision.get("candidate_a_id").toString());
        UUID b = UUID.fromString(decision.get("candidate_b_id").toString());
        // Whichever side was soft-deleted gets restored.
        int restored = jdbc.update("""
                UPDATE ontology_objects
                   SET deleted_at = NULL,
                       merged_into = NULL,
                       merged_at = NULL,
                       merged_by = NULL
                 WHERE id IN (?::uuid, ?::uuid)
                   AND deleted_at IS NOT NULL
                """, a.toString(), b.toString());
        jdbc.update("""
                UPDATE resolution_decisions
                   SET status = 'REJECTED', resolved_at = NOW(), resolved_by = ?
                 WHERE id = ?::uuid
                """, resolvedBy, decisionId.toString());
        log.info("Unmerged decision={} (restored {} objects)", decisionId, restored);
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
