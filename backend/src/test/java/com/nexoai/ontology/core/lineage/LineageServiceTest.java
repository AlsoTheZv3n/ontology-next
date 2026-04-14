package com.nexoai.ontology.core.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies LineageService.recordDiff correctly produces per-field batch inserts,
 * skips no-op updates, and serializes nested JSON values into the value columns.
 *
 * Uses Mockito over JdbcTemplate rather than a real DB: the interesting logic is
 * the diff + batch construction, and exercising it against an in-memory DB would
 * not add meaningful coverage.
 */
class LineageServiceTest {

    private JdbcTemplate jdbc;
    private LineageService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new LineageService(jdbc);
        mapper = new ObjectMapper();
    }

    @Test
    void records_one_entry_per_changed_field() throws Exception {
        JsonNode oldProps = mapper.readTree("{\"name\":\"Acme\",\"revenue\":100}");
        JsonNode patch = mapper.readTree("{\"name\":\"Acme Inc\",\"revenue\":200}");

        int count = service.recordDiff(UUID.randomUUID(), oldProps, patch,
                LineageService.SourceType.USER, "req-1", "api", "alice@example.com");

        assertThat(count).isEqualTo(2);
        List<Object[]> rows = captureBatchRows();
        assertThat(rows).hasSize(2);
    }

    @Test
    void identical_values_are_skipped() throws Exception {
        JsonNode oldProps = mapper.readTree("{\"name\":\"Acme\",\"revenue\":100}");
        JsonNode patch = mapper.readTree("{\"name\":\"Acme\",\"revenue\":200}");

        int count = service.recordDiff(UUID.randomUUID(), oldProps, patch,
                LineageService.SourceType.CONNECTOR, "sync-1", "salesforce", "system");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void no_changes_skips_db_entirely() throws Exception {
        JsonNode oldProps = mapper.readTree("{\"name\":\"Acme\"}");
        JsonNode patch = mapper.readTree("{\"name\":\"Acme\"}");

        int count = service.recordDiff(UUID.randomUUID(), oldProps, patch,
                LineageService.SourceType.USER, "req", "api", "alice");

        assertThat(count).isEqualTo(0);
        verifyNoInteractions(jdbc);
    }

    @Test
    void new_field_on_empty_old_props_is_recorded_with_null_old_value() throws Exception {
        UUID objectId = UUID.randomUUID();
        JsonNode patch = mapper.readTree("{\"email\":\"a@b.com\"}");

        int count = service.recordDiff(objectId, null, patch,
                LineageService.SourceType.CDC, "kafka:offset:42", "customers", "debezium");

        assertThat(count).isEqualTo(1);
        List<Object[]> rows = captureBatchRows();
        assertThat(rows).hasSize(1);
        // columns: id, object_id, property_name, source_type, source_id, source_name, old_value, new_value, changed_by
        assertThat(rows.get(0)[2]).isEqualTo("email");
        assertThat(rows.get(0)[3]).isEqualTo("CDC");
        assertThat(rows.get(0)[6]).isNull();
        assertThat(rows.get(0)[7]).isEqualTo("a@b.com");
    }

    @Test
    void nested_json_values_are_serialized_to_json_string() throws Exception {
        JsonNode patch = mapper.readTree("{\"address\":{\"city\":\"Zurich\"}}");

        service.recordDiff(UUID.randomUUID(), null, patch,
                LineageService.SourceType.AGENT, "session-1", "agent", "bot");

        List<Object[]> rows = captureBatchRows();
        assertThat(rows.get(0)[7]).isEqualTo("{\"city\":\"Zurich\"}");
    }

    @Test
    void null_object_id_or_null_new_props_returns_zero() throws Exception {
        JsonNode patch = mapper.readTree("{\"x\":1}");
        assertThat(service.recordDiff(null, null, patch,
                LineageService.SourceType.USER, null, null, null)).isEqualTo(0);
        assertThat(service.recordDiff(UUID.randomUUID(), null, null,
                LineageService.SourceType.USER, null, null, null)).isEqualTo(0);
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object[]> captureBatchRows() throws Exception {
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCap =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(sqlCap.capture(), setterCap.capture());

        BatchPreparedStatementSetter setter = setterCap.getValue();
        int size = setter.getBatchSize();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            PreparedStatement ps = mock(PreparedStatement.class);
            Object[] captured = new Object[10];
            doAnswer(inv -> { captured[inv.getArgument(0, Integer.class)] = inv.getArgument(1); return null; })
                    .when(ps).setString(any(Integer.class), any());
            doAnswer(inv -> { captured[inv.getArgument(0, Integer.class)] = null; return null; })
                    .when(ps).setString(any(Integer.class), eq(null));
            setter.setValues(ps, i);
            Object[] normalized = new Object[9];
            for (int c = 1; c <= 9; c++) normalized[c - 1] = captured[c];
            rows.add(normalized);
        }
        return rows;
    }
}
