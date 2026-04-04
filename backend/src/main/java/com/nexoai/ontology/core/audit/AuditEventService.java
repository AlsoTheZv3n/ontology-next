package com.nexoai.ontology.core.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventService {

    private final JdbcTemplate jdbcTemplate;

    @Async
    public void record(UUID tenantId, String category, String eventType, String actor,
                       String resourceType, UUID resourceId, String details, String ipAddress) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO audit_events (tenant_id, category, event_type, actor,
                        resource_type, resource_id, details, ip_address)
                    VALUES (?::uuid, ?, ?, ?, ?, ?::uuid, ?::jsonb, ?)
                    """,
                    tenantId.toString(), category, eventType, actor,
                    resourceType, resourceId != null ? resourceId.toString() : null,
                    details != null ? details : "{}", ipAddress);
        } catch (Exception e) {
            log.error("Failed to record audit event for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    public List<Map<String, Object>> query(UUID tenantId, String category,
                                            OffsetDateTime from, OffsetDateTime to, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM audit_events WHERE tenant_id = ?::uuid");
        var params = new java.util.ArrayList<Object>();
        params.add(tenantId.toString());

        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (from != null) {
            sql.append(" AND created_at >= ?");
            params.add(java.sql.Timestamp.from(from.toInstant()));
        }
        if (to != null) {
            sql.append(" AND created_at <= ?");
            params.add(java.sql.Timestamp.from(to.toInstant()));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(Math.min(limit, 1000));

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public String exportCsv(UUID tenantId, OffsetDateTime from, OffsetDateTime to) {
        List<Map<String, Object>> events = query(tenantId, null, from, to, 10000);

        StringBuilder csv = new StringBuilder();
        csv.append("id,tenant_id,category,event_type,actor,resource_type,resource_id,details,ip_address,created_at\n");

        for (Map<String, Object> event : events) {
            csv.append(csvValue(event.get("id")))
               .append(",").append(csvValue(event.get("tenant_id")))
               .append(",").append(csvValue(event.get("category")))
               .append(",").append(csvValue(event.get("event_type")))
               .append(",").append(csvValue(event.get("actor")))
               .append(",").append(csvValue(event.get("resource_type")))
               .append(",").append(csvValue(event.get("resource_id")))
               .append(",").append(csvValue(event.get("details")))
               .append(",").append(csvValue(event.get("ip_address")))
               .append(",").append(csvValue(event.get("created_at")))
               .append("\n");
        }

        return csv.toString();
    }

    public int gdprErase(UUID tenantId, String email) {
        return jdbcTemplate.update(
                """
                UPDATE audit_events SET actor = 'ANONYMIZED',
                    details = '{}'::jsonb
                WHERE tenant_id = ?::uuid AND actor = ?
                """,
                tenantId.toString(), email);
    }

    private String csvValue(Object value) {
        if (value == null) return "";
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
