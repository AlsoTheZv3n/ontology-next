package com.nexoai.ontology.core.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineageService {

    private final JdbcTemplate jdbcTemplate;

    /** Where a property change came from. Maps to property_lineage.source_type. */
    public enum SourceType { USER, CONNECTOR, ACTION, AGENT, CDC, MIGRATION, SYSTEM }

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

    /**
     * Diff two property snapshots and record one entry per field whose value changed.
     * Fields only present in newProps are recorded with old=null; fields only in oldProps
     * are treated as unchanged (we don't emit "deleted field" events yet). Identical values
     * are skipped so repeated writes don't pollute the log.
     *
     * @return the number of lineage rows written.
     */
    public int recordDiff(UUID objectId, JsonNode oldProps, JsonNode newProps,
                           SourceType sourceType, String sourceId, String sourceName,
                           String changedBy) {
        if (objectId == null || newProps == null) return 0;

        List<Change> changes = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = newProps.fields();
        while (it.hasNext()) {
            var e = it.next();
            JsonNode oldV = oldProps == null ? null : oldProps.get(e.getKey());
            JsonNode newV = e.getValue();
            if (Objects.equals(oldV, newV)) continue;
            changes.add(new Change(e.getKey(), jsonText(oldV), jsonText(newV)));
        }
        if (changes.isEmpty()) return 0;

        String sql = """
                INSERT INTO property_lineage (id, object_id, property_name, source_type, source_id,
                                               source_name, old_value, new_value, changed_by)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                Change c = changes.get(i);
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, objectId.toString());
                ps.setString(3, c.property());
                ps.setString(4, sourceType.name());
                ps.setString(5, sourceId);
                ps.setString(6, sourceName);
                ps.setString(7, c.oldValue());
                ps.setString(8, c.newValue());
                ps.setString(9, changedBy);
            }
            @Override public int getBatchSize() { return changes.size(); }
        });
        log.debug("Lineage batch recorded: object={} changes={} source={}",
                objectId, changes.size(), sourceType);
        return changes.size();
    }

    private static String jsonText(JsonNode n) {
        if (n == null || n.isNull()) return null;
        return n.isValueNode() ? n.asText() : n.toString();
    }

    private record Change(String property, String oldValue, String newValue) {}

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
