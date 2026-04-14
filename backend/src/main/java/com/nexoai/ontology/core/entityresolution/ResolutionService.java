package com.nexoai.ontology.core.entityresolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.exception.OntologyException;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence + approve/reject workflow for resolution_decisions.
 * Writes are tenant-scoped via TenantContext (enforced by RLS after prod-04).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResolutionService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public void createPending(UUID tenantId, UUID objectTypeId,
                               UUID candidateA, UUID candidateB,
                               String matchType, double confidence,
                               Map<String, Object> features) {
        try {
            jdbc.update("""
                    INSERT INTO resolution_decisions
                      (tenant_id, object_type_id, candidate_a_id, candidate_b_id,
                       match_type, confidence, features)
                    VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?::jsonb)
                    ON CONFLICT (tenant_id, candidate_a_id, candidate_b_id) DO NOTHING
                    """,
                    tenantId.toString(),
                    objectTypeId.toString(),
                    candidateA.toString(),
                    candidateB.toString(),
                    matchType,
                    confidence,
                    objectMapper.writeValueAsString(features));
            log.info("Resolution decision created: a={} b={} type={} conf={}",
                    candidateA, candidateB, matchType, confidence);
        } catch (Exception e) {
            log.error("Failed to persist resolution decision: {}", e.getMessage());
        }
    }

    public void markAutoMerged(UUID tenantId, UUID objectTypeId,
                                UUID candidateA, UUID candidateB,
                                String matchType, double confidence) {
        jdbc.update("""
                INSERT INTO resolution_decisions
                  (tenant_id, object_type_id, candidate_a_id, candidate_b_id,
                   match_type, confidence, status, resolved_at, resolved_by)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, 'AUTO_MERGED', NOW(), 'system')
                ON CONFLICT (tenant_id, candidate_a_id, candidate_b_id) DO NOTHING
                """,
                tenantId.toString(),
                objectTypeId.toString(),
                candidateA.toString(),
                candidateB.toString(),
                matchType,
                confidence);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pending(int limit) {
        UUID tid = TenantContext.getTenantId();
        return jdbc.queryForList("""
                SELECT id, candidate_a_id, candidate_b_id, match_type, confidence, features, created_at
                FROM resolution_decisions
                WHERE tenant_id = ?::uuid AND status = 'PENDING'
                ORDER BY confidence DESC, created_at DESC
                LIMIT ?
                """,
                tid.toString(), limit);
    }

    public Map<String, Object> approve(UUID decisionId, String resolvedBy) {
        int updated = jdbc.update("""
                UPDATE resolution_decisions
                SET status = 'APPROVED', resolved_at = NOW(), resolved_by = ?
                WHERE id = ?::uuid AND status = 'PENDING'
                """, resolvedBy, decisionId.toString());
        if (updated == 0) {
            throw new OntologyException("Resolution decision not found or not pending: " + decisionId);
        }
        return Map.of("id", decisionId.toString(), "status", "APPROVED");
    }

    public Map<String, Object> reject(UUID decisionId, String resolvedBy) {
        int updated = jdbc.update("""
                UPDATE resolution_decisions
                SET status = 'REJECTED', resolved_at = NOW(), resolved_by = ?
                WHERE id = ?::uuid AND status = 'PENDING'
                """, resolvedBy, decisionId.toString());
        if (updated == 0) {
            throw new OntologyException("Resolution decision not found or not pending: " + decisionId);
        }
        return Map.of("id", decisionId.toString(), "status", "REJECTED");
    }
}
