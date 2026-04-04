package com.nexoai.ontology.core.lineage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineageService {

    private final JdbcTemplate jdbcTemplate;

    public void recordChange(UUID objectId, String propertyName, String sourceType,
                              String sourceId, String sourceName,
                              String oldValue, String newValue, String changedBy) {
        UUID lineageId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO property_lineage (id, object_id, property_name, source_type, source_id,
                                               source_name, old_value, new_value, changed_by)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
                """,
                lineageId.toString(), objectId.toString(), propertyName, sourceType,
                sourceId, sourceName, oldValue, newValue, changedBy);

        log.debug("Lineage recorded for object {}, property {}: {} -> {}",
                objectId, propertyName, oldValue, newValue);
    }

    public List<Map<String, Object>> getObjectLineage(UUID objectId) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM property_lineage
                WHERE object_id = ?::uuid
                ORDER BY changed_at DESC
                """,
                objectId.toString());
    }

    public List<Map<String, Object>> getPropertyLineage(UUID objectId, String propertyName) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM property_lineage
                WHERE object_id = ?::uuid AND property_name = ?
                ORDER BY changed_at DESC
                """,
                objectId.toString(), propertyName);
    }
}
