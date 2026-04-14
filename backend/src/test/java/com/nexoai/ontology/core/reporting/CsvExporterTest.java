package com.nexoai.ontology.core.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExporterTest {

    private CsvExporter exporter;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        exporter = new CsvExporter(mapper);
    }

    private Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void empty_input_yields_just_a_header_free_line_or_nothing() {
        String csv = exporter.exportRows(List.of(), List.of());
        assertThat(csv).isEqualTo("\r\n"); // header row is empty but still terminated
    }

    @Test
    void simple_rows_produce_comma_separated_values_with_CRLF_line_ends() {
        String csv = exporter.exportRows(List.of(
                row("name", "Acme", "revenue", 100),
                row("name", "Globex", "revenue", 200)
        ), List.of("name", "revenue"));

        assertThat(csv).isEqualTo("name,revenue\r\nAcme,100\r\nGlobex,200\r\n");
    }

    @Test
    void header_order_defaults_to_alphabetical_union_of_keys() {
        String csv = exporter.exportRows(List.of(
                row("z", "1", "a", "2"),
                row("m", "3")
        ), null);

        assertThat(csv.lines().findFirst()).contains("a,m,z");
    }

    @Test
    void missing_keys_render_as_empty_fields() {
        String csv = exporter.exportRows(List.of(
                row("a", "1", "b", "2"),
                row("a", "3") // no "b"
        ), List.of("a", "b"));

        assertThat(csv).isEqualTo("a,b\r\n1,2\r\n3,\r\n");
    }

    @Test
    void fields_with_commas_are_quoted() {
        String csv = exporter.exportRows(List.of(
                row("name", "Acme, Inc.", "note", "ok")
        ), List.of("name", "note"));

        assertThat(csv).contains("\"Acme, Inc.\",ok");
    }

    @Test
    void embedded_quotes_are_doubled_and_wrapped() {
        String csv = exporter.exportRows(List.of(
                row("quote", "He said \"hello\"")
        ), List.of("quote"));

        // "He said ""hello"""
        assertThat(csv).contains("\"He said \"\"hello\"\"\"");
    }

    @Test
    void embedded_newlines_are_quoted() {
        String csv = exporter.exportRows(List.of(
                row("desc", "line 1\nline 2")
        ), List.of("desc"));

        assertThat(csv).contains("\"line 1\nline 2\"");
    }

    @Test
    void null_and_nonstring_values_are_stringified_safely() {
        String csv = exporter.exportRows(List.of(
                row("a", null, "b", 42, "c", true)
        ), List.of("a", "b", "c"));

        assertThat(csv).isEqualTo("a,b,c\r\n,42,true\r\n");
    }

    @Test
    void json_object_rows_flatten_to_column_map() throws Exception {
        List<JsonNode> rows = List.of(
                mapper.readTree("{\"name\":\"Acme\",\"email\":\"a@b.ch\"}"),
                mapper.readTree("{\"name\":\"Globex\",\"revenue\":500}")
        );

        String csv = exporter.exportProperties(rows, List.of("name", "email", "revenue"));

        assertThat(csv).isEqualTo(
                "name,email,revenue\r\n" +
                "Acme,a@b.ch,\r\n" +
                "Globex,,500\r\n"
        );
    }

    @Test
    void nested_json_objects_serialize_to_their_json_string_form() throws Exception {
        List<JsonNode> rows = List.of(
                mapper.readTree("{\"name\":\"Acme\",\"address\":{\"city\":\"Zurich\"}}")
        );

        String csv = exporter.exportProperties(rows, List.of("name", "address"));

        assertThat(csv).contains("\"{\"\"city\"\":\"\"Zurich\"\"}\"");
    }

    @Test
    void escape_of_plain_string_returns_input_unchanged() {
        assertThat(CsvExporter.escape("hello world")).isEqualTo("hello world");
        assertThat(CsvExporter.escape("")).isEmpty();
        assertThat(CsvExporter.escape(null)).isEmpty();
    }
}
