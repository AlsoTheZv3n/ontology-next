package com.nexoai.ontology.core.reporting;

import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> aggregate(UUID tenantId, String objectType, String operation, String property) {
        String validOp = switch (operation.toUpperCase()) {
            case "COUNT" -> "COUNT";
            case "SUM" -> "SUM";
            case "AVG" -> "AVG";
            case "MIN" -> "MIN";
            case "MAX" -> "MAX";
            default -> throw new OntologyException("Invalid aggregation operation: " + operation);
        };

        Map<String, Object> result = new HashMap<>();
        result.put("operation", validOp);
        result.put("objectType", objectType);
        result.put("property", property);

        if ("COUNT".equals(validOp)) {
            Long count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM ontology_objects o
                    JOIN object_types ot ON o.object_type_id = ot.id
                    WHERE o.tenant_id = ?::uuid AND ot.api_name = ?
                    """,
                    Long.class,
                    tenantId.toString(), objectType);
            result.put("value", count);
        } else {
            // Aggregate on a JSONB property value (cast to numeric)
            try {
                Object value = jdbcTemplate.queryForObject(
                        String.format("""
                        SELECT %s((o.properties->>?)::numeric) FROM ontology_objects o
                        JOIN object_types ot ON o.object_type_id = ot.id
                        WHERE o.tenant_id = ?::uuid AND ot.api_name = ?
                          AND o.properties->>? IS NOT NULL
                        """, validOp),
                        Object.class,
                        property, tenantId.toString(), objectType, property);
                result.put("value", value);
            } catch (Exception e) {
                result.put("value", null);
                result.put("error", "Aggregation failed: " + e.getMessage());
            }
        }

        return result;
    }

    public Map<String, Object> saveDashboard(UUID tenantId, String name, String description,
                                              String widgets, String layout) {
        UUID dashboardId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO dashboards (id, tenant_id, name, description, widgets, layout)
                VALUES (?::uuid, ?::uuid, ?, ?, ?::jsonb, ?::jsonb)
                """,
                dashboardId.toString(), tenantId.toString(), name, description,
                widgets != null ? widgets : "[]",
                layout != null ? layout : "{}");

        log.info("Dashboard created: {} for tenant {}", name, tenantId);

        return jdbcTemplate.queryForMap(
                "SELECT * FROM dashboards WHERE id = ?::uuid", dashboardId.toString());
    }

    public List<Map<String, Object>> getDashboards(UUID tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM dashboards WHERE tenant_id = ?::uuid ORDER BY created_at DESC",
                tenantId.toString());
    }

    public Map<String, Object> getDashboard(UUID dashboardId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT * FROM dashboards WHERE id = ?::uuid", dashboardId.toString());
        } catch (Exception e) {
            throw new OntologyException("Dashboard not found: " + dashboardId);
        }
    }
}
