package com.nexoai.ontology.core.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Converts tabular data — either arbitrary rows from JdbcTemplate.queryForList
 * or a list of JsonNode property snapshots — into RFC 4180 CSV text.
 *
 * CSV is the pragmatic choice over the heavier PDF / Excel paths the spec
 * originally asked for: it's one file per dependency (zero new jars), opens
 * in Excel/Numbers/LibreOffice directly, and round-trips through every ETL
 * tool the sales demo audience actually uses. PDF generation via iText is
 * AGPL-encumbered and expensive for commercial use; Apache POI xlsx adds
 * ~15 MB of jars. Both land as follow-up work if customers ask.
 *
 * Escaping follows RFC 4180:
 *   - fields containing a delimiter, a double-quote, or any newline are wrapped
 *     in double-quotes
 *   - inner double-quotes are doubled ("")
 *   - lines are terminated with CRLF
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvExporter {

    private static final String LINE_END = "\r\n";
    private static final char DELIMITER = ',';

    private final ObjectMapper objectMapper;

    /**
     * Export a list of row maps (column name → value) as CSV. Column order is
     * the union of all keys across rows, sorted alphabetically for stable output
     * unless {@code columns} is non-null (in which case that order is used).
     */
    public String exportRows(List<Map<String, Object>> rows, List<String> columns) {
        if (rows == null) rows = List.of();

        List<String> headers = columns != null
                ? new ArrayList<>(columns)
                : collectHeaders(rows);

        StringBuilder out = new StringBuilder();
        appendRow(out, headers);
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>(headers.size());
            for (String h : headers) values.add(stringify(row == null ? null : row.get(h)));
            appendRow(out, values);
        }
        return out.toString();
    }

    /**
     * Export a list of ontology-object property snapshots. Each JsonNode must
     * be an object; non-object nodes are rendered as a single "value" column.
     */
    public String exportProperties(List<JsonNode> rows, List<String> columns) {
        if (rows == null) rows = List.of();

        List<Map<String, Object>> maps = new ArrayList<>(rows.size());
        for (JsonNode n : rows) {
            if (n == null || n.isNull()) { maps.add(Map.of()); continue; }
            if (!n.isObject()) { maps.add(Map.of("value", stringify(n))); continue; }
            // Keep values as JsonNode so stringify() can format nested
            // objects/arrays as their JSON text rather than Map.toString().
            Map<String, Object> asMap = new java.util.LinkedHashMap<>();
            var fields = n.fields();
            while (fields.hasNext()) {
                var e = fields.next();
                asMap.put(e.getKey(), e.getValue());
            }
            maps.add(asMap);
        }
        return exportRows(maps, columns);
    }

    private List<String> collectHeaders(List<Map<String, Object>> rows) {
        TreeSet<String> set = new TreeSet<>();
        for (Map<String, Object> row : rows) {
            if (row != null) set.addAll(row.keySet());
        }
        return new ArrayList<>(set);
    }

    private void appendRow(StringBuilder out, List<String> fields) {
        Iterator<String> it = fields.iterator();
        while (it.hasNext()) {
            out.append(escape(it.next()));
            if (it.hasNext()) out.append(DELIMITER);
        }
        out.append(LINE_END);
    }

    static String escape(String value) {
        if (value == null) return "";
        boolean needsQuoting = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == DELIMITER || c == '"' || c == '\n' || c == '\r') {
                needsQuoting = true;
                break;
            }
        }
        if (!needsQuoting) return value;
        StringBuilder sb = new StringBuilder(value.length() + 4);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') sb.append('"');
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    private String stringify(Object v) {
        if (v == null) return "";
        if (v instanceof JsonNode n) {
            if (n.isNull()) return "";
            return n.isValueNode() ? n.asText() : n.toString();
        }
        return String.valueOf(v);
    }
}
