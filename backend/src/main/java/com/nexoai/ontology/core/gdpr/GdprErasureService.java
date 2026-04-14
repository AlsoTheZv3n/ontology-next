package com.nexoai.ontology.core.gdpr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GDPR Right-to-be-Forgotten (Art. 17) pipeline.
 *
 * The previous implementation only anonymized audit_events.actor_email, which
 * left personal data intact in ontology_objects.properties and therefore did
 * not satisfy the regulation. This service walks every object whose JSONB
 * properties contain the subject email (including nested objects and arrays),
 * rewrites the offending fields, and logs the erasure in gdpr_erasure_log for
 * future DSAR proofs.
 *
 * Redaction policy (chosen for referential integrity — we anonymize, we don't
 * hard-delete the object row):
 *   - any string value that contains the subject email →
 *     "deleted-{randomHex12}@example.invalid"
 *   - NAME_KEYS (name, firstName, lastName, fullName, displayName) → "[Redacted]"
 *   - "phone", "address" → null
 *   - embedding column → NULL (residual semantic info can leak the subject)
 *
 * The scan uses text-level match + recursive JSON walk to handle arbitrary
 * schema depth. For catalogs beyond a few million rows a GIN index on
 * properties jsonb_path_ops plus targeted jsonb_path_query is the next step.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GdprErasureService {

    private static final String REDACTED = "[Redacted]";
    private static final Set<String> NAME_KEYS = Set.of(
            "name", "firstName", "lastName", "fullName", "displayName",
            "first_name", "last_name", "full_name", "display_name");
    private static final Set<String> NULL_KEYS = Set.of(
            "phone", "address", "mobile", "fax", "birthday", "birthdate");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public record EraseReport(
            UUID eraseLogId,
            int objectsAnonymized,
            int auditEventsAnonymized,
            List<UUID> objectIds,
            boolean dryRun
    ) {}

    @Transactional(readOnly = true)
    public EraseReport dryRun(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return scanAndMaybeErase(email, true, null);
    }

    @Transactional
    public EraseReport eraseByEmail(String email, String initiatedBy, String reason) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        UUID tenantId = TenantContext.getTenantIdOrNull();
        String hash = sha256(email.toLowerCase(Locale.ROOT));

        if (tenantId != null && alreadyCompleted(tenantId, hash)) {
            throw new IllegalStateException("Email already erased");
        }

        UUID logId = UUID.randomUUID();
        if (tenantId != null) {
            try {
                jdbc.update("""
                        INSERT INTO gdpr_erasure_log
                          (id, tenant_id, subject_email, subject_email_hash, initiated_by, reason, status)
                        VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, 'PENDING')
                        """,
                        logId.toString(), tenantId.toString(), email, hash,
                        initiatedBy, reason);
            } catch (Exception e) {
                log.warn("Could not record gdpr_erasure_log entry: {}", e.getMessage());
            }
        }

        EraseReport report = scanAndMaybeErase(email, false, logId);

        if (tenantId != null) {
            try {
                jdbc.update("""
                        UPDATE gdpr_erasure_log
                           SET status = 'COMPLETED',
                               affected_count = ?,
                               audit_events_anonymized = ?,
                               completed_at = NOW(),
                               affected_objects = ?::jsonb
                         WHERE id = ?::uuid
                        """,
                        report.objectsAnonymized(), report.auditEventsAnonymized(),
                        serializeIds(report.objectIds()), logId.toString());
            } catch (Exception e) {
                log.warn("Could not update gdpr_erasure_log: {}", e.getMessage());
            }
        }
        return report;
    }

    private EraseReport scanAndMaybeErase(String email, boolean dryRun, UUID logId) {
        String lower = email.toLowerCase(Locale.ROOT);

        // ILIKE pre-filter narrows candidates before the exact JSON walk.
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT id, properties FROM ontology_objects
                     WHERE properties::text ILIKE ?
                    """, "%" + lower + "%");
        } catch (Exception e) {
            log.error("GDPR scan failed: {}", e.getMessage());
            return new EraseReport(logId, 0, 0, List.of(), dryRun);
        }

        List<UUID> ids = new ArrayList<>();
        int anonymized = 0;
        for (Map<String, Object> row : rows) {
            UUID id = toUuid(row.get("id"));
            if (id == null) continue;
            try {
                JsonNode props = mapper.readTree(String.valueOf(row.get("properties")));
                if (!containsEmail(props, lower)) continue; // ILIKE false positive
                ids.add(id);
                if (!dryRun) {
                    JsonNode redacted = redact(props, lower);
                    jdbc.update("""
                            UPDATE ontology_objects
                               SET properties = ?::jsonb,
                                   embedding = NULL,
                                   updated_at = NOW()
                             WHERE id = ?::uuid
                            """, redacted.toString(), id.toString());
                    anonymized++;
                }
            } catch (Exception e) {
                log.error("Failed to redact object {}: {}", id, e.getMessage());
            }
        }

        int auditRows = 0;
        if (!dryRun) {
            try {
                auditRows = jdbc.update("""
                        UPDATE audit_events
                           SET actor_email = 'deleted-' || substring(md5(random()::text), 1, 12) || '@example.invalid',
                               actor_user_id = NULL,
                               metadata = COALESCE(metadata, '{}'::jsonb) - 'email' - 'name' - 'phone'
                         WHERE actor_email = ?
                        """, email);
            } catch (Exception e) {
                log.warn("Could not anonymize audit_events: {}", e.getMessage());
            }
        }

        return new EraseReport(logId, dryRun ? ids.size() : anonymized, auditRows, ids, dryRun);
    }

    private boolean alreadyCompleted(UUID tenantId, String hash) {
        try {
            Boolean exists = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM gdpr_erasure_log
                         WHERE tenant_id = ?::uuid
                           AND subject_email_hash = ?
                           AND status = 'COMPLETED'
                    )
                    """, Boolean.class, tenantId.toString(), hash);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Could not check gdpr_erasure_log: {}", e.getMessage());
            return false;
        }
    }

    /** True if any string value anywhere in the tree contains {@code lower}. */
    static boolean containsEmail(JsonNode n, String lower) {
        if (n == null) return false;
        if (n.isTextual()) return n.asText("").toLowerCase(Locale.ROOT).contains(lower);
        if (n.isObject()) {
            var it = n.fields();
            while (it.hasNext()) if (containsEmail(it.next().getValue(), lower)) return true;
        }
        if (n.isArray()) for (JsonNode c : n) if (containsEmail(c, lower)) return true;
        return false;
    }

    /** Return a redacted deep copy — never mutates the input tree. */
    JsonNode redact(JsonNode n, String emailLower) {
        if (n == null) return null;
        if (n.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            var it = n.fields();
            while (it.hasNext()) {
                var e = it.next();
                String k = e.getKey();
                JsonNode v = e.getValue();
                if (v.isTextual()) {
                    String s = v.asText();
                    if (s.toLowerCase(Locale.ROOT).contains(emailLower)) {
                        copy.put(k, "deleted-" + randomTail() + "@example.invalid");
                    } else if (NAME_KEYS.contains(k)) {
                        copy.put(k, REDACTED);
                    } else if (NULL_KEYS.contains(k.toLowerCase(Locale.ROOT))) {
                        copy.putNull(k);
                    } else {
                        copy.set(k, v);
                    }
                } else {
                    copy.set(k, redact(v, emailLower));
                }
            }
            return copy;
        }
        if (n.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            for (JsonNode c : n) out.add(redact(c, emailLower));
            return out;
        }
        return n;
    }

    private static String randomTail() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static UUID toUuid(Object o) {
        if (o == null) return null;
        if (o instanceof UUID u) return u;
        try { return UUID.fromString(o.toString()); } catch (Exception e) { return null; }
    }

    private String serializeIds(List<UUID> ids) {
        try { return mapper.writeValueAsString(ids); }
        catch (Exception e) { return "[]"; }
    }
}
