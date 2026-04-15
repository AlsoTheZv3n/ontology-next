package com.nexoai.ontology.core.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.core.entityresolution.EntityResolutionEngine;
import com.nexoai.ontology.core.entityresolution.ResolutionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies the bulk-scan + merge orchestration on top of the prod-05 detection engine.
 * Detection itself is already covered by EntityResolutionEngineTest — here we assert
 * that pairs are persisted via ResolutionService and that confirmMerge re-points
 * object_links and removes the loser.
 */
class DuplicateDetectionServiceTest {

    private EntityResolutionEngine engine;
    private ResolutionService resolutionService;
    private JpaOntologyObjectRepository objectRepository;
    private JdbcTemplate jdbc;
    private DuplicateDetectionService service;
    private ObjectMapper mapper;
    private UUID typeId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        engine = mock(EntityResolutionEngine.class);
        resolutionService = mock(ResolutionService.class);
        objectRepository = mock(JpaOntologyObjectRepository.class);
        jdbc = mock(JdbcTemplate.class);
        mapper = new ObjectMapper();
        service = new DuplicateDetectionService(engine, resolutionService, objectRepository,
                jdbc, mapper, new SimpleMeterRegistry());
        typeId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
    }

    private OntologyObjectEntity entity(UUID id, String props) {
        return OntologyObjectEntity.builder()
                .id(id).objectTypeId(typeId).properties(props).createdAt(Instant.now()).build();
    }

    @Test
    void scan_persists_one_decision_per_detected_pair() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(objectRepository.findAll()).thenReturn(List.of(
                entity(a, "{\"email\":\"x@y.ch\"}"),
                entity(b, "{\"email\":\"x@y.ch\"}")
        ));
        when(engine.findDuplicates(any(), anyInt()))
                .thenReturn(List.of(new EntityResolutionEngine.Candidate(b, "EXACT", 0.99,
                        Map.of("exact_field", "email"))))
                .thenReturn(List.of(new EntityResolutionEngine.Candidate(a, "EXACT", 0.99,
                        Map.of("exact_field", "email"))));

        var report = service.scanObjectType(tenantId, typeId);

        assertThat(report.objectsScanned()).isEqualTo(2);
        assertThat(report.pairsFound()).isEqualTo(2);
        verify(resolutionService, times(2)).createPending(eq(tenantId), eq(typeId),
                any(UUID.class), any(UUID.class), eq("EXACT"), eq(0.99), any());
    }

    @Test
    void scan_filters_out_objects_of_other_types() {
        UUID otherType = UUID.randomUUID();
        OntologyObjectEntity wrongType = OntologyObjectEntity.builder()
                .id(UUID.randomUUID()).objectTypeId(otherType)
                .properties("{}").createdAt(Instant.now()).build();
        when(objectRepository.findAll()).thenReturn(List.of(wrongType));

        var report = service.scanObjectType(tenantId, typeId);

        assertThat(report.objectsScanned()).isEqualTo(0);
        verifyNoInteractions(engine);
        verifyNoInteractions(resolutionService);
    }

    @Test
    void confirmMerge_repoints_links_and_soft_deletes_loser() {
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("candidate_a_id", winner.toString());
        row.put("candidate_b_id", loser.toString());
        row.put("tenant_id", tenantId.toString());
        row.put("object_type_id", typeId.toString());
        row.put("match_type", "EXACT");
        row.put("confidence", 0.99);
        when(jdbc.queryForMap(anyString(), eq(decisionId.toString()))).thenReturn(row);

        service.confirmMerge(decisionId, winner, "alice");

        // Expect a soft-delete UPDATE on ontology_objects, not a hard delete.
        verify(jdbc).update(
                argThat((String sql) -> sql != null
                        && sql.contains("UPDATE ontology_objects")
                        && sql.contains("deleted_at")
                        && sql.contains("merged_into")),
                eq(winner.toString()), eq("alice"), eq(loser.toString()));
        verify(objectRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void unmerge_clears_deleted_at_and_rejects_decision() {
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("candidate_a_id", winner.toString());
        row.put("candidate_b_id", loser.toString());
        when(jdbc.queryForMap(anyString(), eq(decisionId.toString()))).thenReturn(row);
        when(jdbc.update(argThat((String sql) -> sql != null && sql.contains("deleted_at = NULL")),
                anyString(), anyString())).thenReturn(1);

        service.unmerge(decisionId, "bob");

        verify(jdbc).update(
                argThat((String sql) -> sql != null && sql.contains("deleted_at = NULL")),
                eq(winner.toString()), eq(loser.toString()));
        verify(jdbc).update(
                argThat((String sql) -> sql != null && sql.contains("status = 'REJECTED'")),
                eq("bob"), eq(decisionId.toString()));
    }

    @Test
    void confirmMerge_rejects_winnerId_not_in_pair() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("candidate_a_id", a.toString());
        row.put("candidate_b_id", b.toString());
        row.put("tenant_id", tenantId.toString());
        row.put("object_type_id", typeId.toString());
        row.put("match_type", "EXACT");
        row.put("confidence", 0.99);
        when(jdbc.queryForMap(anyString(), eq(decisionId.toString()))).thenReturn(row);

        assertThatThrownBy(() -> service.confirmMerge(decisionId, stranger, "alice"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(objectRepository, never()).deleteById(any(UUID.class));
    }
}
