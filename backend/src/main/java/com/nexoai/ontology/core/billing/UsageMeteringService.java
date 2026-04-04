package com.nexoai.ontology.core.billing;

import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageMeteringService {

    private final JdbcTemplate jdbcTemplate;

    @Async
    public void recordEvent(UUID tenantId, String eventType, UUID resourceId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO usage_events (tenant_id, event_type, resource_id) VALUES (?::uuid, ?, ?::uuid)",
                    tenantId.toString(), eventType, resourceId != null ? resourceId.toString() : null);
        } catch (Exception e) {
            log.error("Failed to record usage event for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    public Map<String, Object> getUsageSummary(UUID tenantId) {
        Map<String, Object> summary = new HashMap<>();

        var rows = jdbcTemplate.queryForList(
                """
                SELECT event_type, COUNT(*) as cnt
                FROM usage_events
                WHERE tenant_id = ?::uuid
                  AND created_at >= date_trunc('month', NOW())
                GROUP BY event_type
                """,
                tenantId.toString());

        for (var row : rows) {
            summary.put((String) row.get("event_type"), row.get("cnt"));
        }

        return summary;
    }

    public void checkLimit(UUID tenantId, String limitType) {
        var planRow = jdbcTemplate.queryForMap(
                """
                SELECT p.* FROM plans p
                JOIN tenants t ON t.plan = p.id
                WHERE t.id = ?::uuid
                """,
                tenantId.toString());

        long currentCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM usage_events
                WHERE tenant_id = ?::uuid
                  AND event_type = ?
                  AND created_at >= date_trunc('month', NOW())
                """,
                Long.class,
                tenantId.toString(), limitType);

        Integer maxAllowed = switch (limitType) {
            case "AGENT_CALL" -> (Integer) planRow.get("max_agent_calls_per_month");
            case "SYNC_RUN" -> (Integer) planRow.get("max_sync_runs_per_month");
            default -> null;
        };

        if (maxAllowed != null && currentCount >= maxAllowed) {
            throw new OntologyException("Usage limit exceeded for " + limitType
                    + ". Current: " + currentCount + ", Max: " + maxAllowed);
        }
    }
}
