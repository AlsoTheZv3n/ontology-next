package com.nexoai.ontology.core.gdpr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-level coverage for the redaction logic and the erase/dry-run workflow.
 *
 * The heart of the service is the recursive JSON walker; those assertions run
 * directly against redact() and containsEmail() without mocking. The database
 * interactions are covered by mocking JdbcTemplate — good enough to verify
 * that the right statements fire in the right order.
 */
class GdprErasureServiceTest {

    private JdbcTemplate jdbc;
    private ObjectMapper mapper;
    private GdprErasureService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        mapper = new ObjectMapper();
        service = new GdprErasureService(jdbc, mapper);
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() { TenantContext.clear(); }

    // ---------------------------------------------------------------
    // redact() — pure logic, no DB involved.

    @Test
    void redact_replaces_top_level_email_and_name() throws Exception {
        JsonNode in = mapper.readTree(
                "{\"name\":\"Hans Muster\",\"email\":\"user@x.ch\",\"phone\":\"+41 79 111\"}");
        JsonNode out = service.redact(in, "user@x.ch");

        assertThat(out.path("email").asText())
                .startsWith("deleted-")
                .endsWith("@example.invalid");
        assertThat(out.path("name").asText()).isEqualTo("[Redacted]");
        assertThat(out.path("phone").isNull()).isTrue();
    }

    @Test
    void redact_walks_into_nested_objects() throws Exception {
        JsonNode in = mapper.readTree(
                "{\"reporter\":{\"email\":\"user@x.ch\",\"name\":\"Hans\"},\"title\":\"Bug\"}");
        JsonNode out = service.redact(in, "user@x.ch");

        assertThat(out.toString()).doesNotContain("user@x.ch");
        assertThat(out.path("reporter").path("name").asText()).isEqualTo("[Redacted]");
        assertThat(out.path("title").asText()).isEqualTo("Bug"); // untouched
    }

    @Test
    void redact_walks_into_arrays() throws Exception {
        JsonNode in = mapper.readTree("""
                {"contacts":[{"email":"user@x.ch","name":"Hans"},{"email":"other@y.ch"}]}
                """);
        JsonNode out = service.redact(in, "user@x.ch");

        assertThat(out.path("contacts").get(0).path("email").asText())
                .startsWith("deleted-");
        // Second contact was unrelated to the subject — must remain unchanged.
        assertThat(out.path("contacts").get(1).path("email").asText()).isEqualTo("other@y.ch");
    }

    @Test
    void containsEmail_detects_nested_and_array_values() throws Exception {
        JsonNode n1 = mapper.readTree("{\"a\":{\"b\":[{\"c\":\"USER@x.ch\"}]}}");
        assertThat(GdprErasureService.containsEmail(n1, "user@x.ch")).isTrue();

        JsonNode n2 = mapper.readTree("{\"a\":\"nothing here\"}");
        assertThat(GdprErasureService.containsEmail(n2, "user@x.ch")).isFalse();
    }

    // ---------------------------------------------------------------
    // Full service flow with a mocked JdbcTemplate.

    @SuppressWarnings("unchecked")
    private void stubObjectRows(Map<String, Object>... rows) {
        when(jdbc.queryForList(contains("FROM ontology_objects"), any(Object[].class)))
                .thenReturn(List.of(rows));
    }

    private Map<String, Object> objectRow(UUID id, String propsJson) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        r.put("properties", propsJson);
        return r;
    }

    @Test
    void erase_anonymizes_matching_object_and_records_audit_counter() {
        UUID id = UUID.randomUUID();
        stubObjectRows(objectRow(id,
                "{\"name\":\"Hans\",\"email\":\"user@x.ch\",\"phone\":\"+41\"}"));
        when(jdbc.update(contains("UPDATE audit_events"), eq("user@x.ch")))
                .thenReturn(5);

        var report = service.eraseByEmail("user@x.ch", "admin@nexo.ai", "DSAR-001");

        assertThat(report.objectsAnonymized()).isEqualTo(1);
        assertThat(report.auditEventsAnonymized()).isEqualTo(5);
        assertThat(report.objectIds()).contains(id);
        assertThat(report.dryRun()).isFalse();
    }

    @Test
    void dry_run_reports_matches_without_writing() {
        UUID id = UUID.randomUUID();
        stubObjectRows(objectRow(id, "{\"email\":\"user@x.ch\"}"));

        var report = service.dryRun("user@x.ch");

        assertThat(report.dryRun()).isTrue();
        assertThat(report.objectsAnonymized()).isEqualTo(1);
        assertThat(report.objectIds()).contains(id);
        // Only the SELECT was issued — no UPDATE on properties or audit_events.
        verify(jdbc, never()).update(contains("UPDATE ontology_objects"), anyString(), anyString());
        verify(jdbc, never()).update(contains("UPDATE audit_events"), anyString());
    }

    @Test
    void ilike_false_positive_does_not_produce_anonymization() {
        // Object contains the email substring only by coincidence (not as a value).
        UUID id = UUID.randomUUID();
        stubObjectRows(objectRow(id, "{\"note\":\"unrelated 'user@y.ch' text\"}"));

        var report = service.dryRun("user@x.ch"); // substring miss

        assertThat(report.objectsAnonymized()).isEqualTo(0);
    }

    @Test
    void blank_email_is_rejected() {
        assertThatThrownBy(() -> service.eraseByEmail("", "admin", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.dryRun(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void second_erase_of_same_email_within_tenant_is_blocked() {
        UUID tenant = UUID.randomUUID();
        TenantContext.setTenantId(tenant);
        when(jdbc.queryForObject(contains("EXISTS"), eq(Boolean.class),
                eq(tenant.toString()), anyString()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.eraseByEmail("user@x.ch", "admin", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already erased");
    }
}
