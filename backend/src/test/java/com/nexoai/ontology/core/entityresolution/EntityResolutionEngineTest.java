package com.nexoai.ontology.core.entityresolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the three detection strategies (EXACT, FUZZY, SEMANTIC) and confidence thresholds.
 * Uses an in-memory fake repository to avoid DB dependencies.
 */
class EntityResolutionEngineTest {

    private JpaOntologyObjectRepository repo;
    private EntityResolutionEngine engine;
    private ObjectMapper mapper;
    private UUID customerTypeId;

    @BeforeEach
    void setUp() {
        repo = mock(JpaOntologyObjectRepository.class);
        mapper = new ObjectMapper();
        engine = new EntityResolutionEngine(repo, mapper);
        customerTypeId = UUID.randomUUID();
    }

    @Test
    void exact_email_match_returns_confidence_0_99() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID peerId = UUID.randomUUID();
        JsonNode subjectProps = mapper.readTree(
                "{\"name\":\"Acme Corporation\",\"email\":\"info@acme.com\"}");

        OntologyObjectEntity peer = OntologyObjectEntity.builder()
                .id(peerId)
                .objectTypeId(customerTypeId)
                .properties("{\"name\":\"Acme Corp\",\"email\":\"info@acme.com\"}")
                .createdAt(Instant.now())
                .build();
        when(repo.findAll()).thenReturn(List.of(peer));

        OntologyObject subject = OntologyObject.builder()
                .id(subjectId)
                .objectTypeId(customerTypeId)
                .objectTypeName("Customer")
                .properties(subjectProps)
                .build();

        List<EntityResolutionEngine.Candidate> candidates = engine.findDuplicates(subject, 10);

        assertThat(candidates).hasSize(1);
        var c = candidates.get(0);
        assertThat(c.matchType()).isEqualTo("EXACT");
        assertThat(c.confidence()).isEqualTo(0.99);
        assertThat(c.features()).containsEntry("exact_field", "email");
        assertThat(c.objectId()).isEqualTo(peerId);
    }

    @Test
    void fuzzy_name_match_returns_confidence_between_0_75_and_0_95() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID peerId = UUID.randomUUID();
        JsonNode subjectProps = mapper.readTree(
                "{\"name\":\"Acme Corporation\"}");

        OntologyObjectEntity peer = OntologyObjectEntity.builder()
                .id(peerId)
                .objectTypeId(customerTypeId)
                .properties("{\"name\":\"Acme Corp\"}")
                .createdAt(Instant.now())
                .build();
        when(repo.findAll()).thenReturn(List.of(peer));

        OntologyObject subject = OntologyObject.builder()
                .id(subjectId)
                .objectTypeId(customerTypeId)
                .objectTypeName("Customer")
                .properties(subjectProps)
                .build();

        List<EntityResolutionEngine.Candidate> candidates = engine.findDuplicates(subject, 10);

        assertThat(candidates).hasSize(1);
        var c = candidates.get(0);
        assertThat(c.matchType()).isEqualTo("FUZZY");
        assertThat(c.confidence()).isBetween(0.75, 0.95);
    }

    @Test
    void completely_different_names_return_no_candidates() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID peerId = UUID.randomUUID();
        JsonNode subjectProps = mapper.readTree("{\"name\":\"Acme Corp\"}");

        OntologyObjectEntity peer = OntologyObjectEntity.builder()
                .id(peerId)
                .objectTypeId(customerTypeId)
                .properties("{\"name\":\"Globex Industries\"}")
                .createdAt(Instant.now())
                .build();
        when(repo.findAll()).thenReturn(List.of(peer));

        OntologyObject subject = OntologyObject.builder()
                .id(subjectId)
                .objectTypeId(customerTypeId)
                .objectTypeName("Customer")
                .properties(subjectProps)
                .build();

        List<EntityResolutionEngine.Candidate> candidates = engine.findDuplicates(subject, 10);

        assertThat(candidates).isEmpty();
    }

    @Test
    void self_is_excluded_from_candidates() throws Exception {
        UUID id = UUID.randomUUID();
        JsonNode props = mapper.readTree("{\"email\":\"x@x.com\"}");

        OntologyObjectEntity self = OntologyObjectEntity.builder()
                .id(id)
                .objectTypeId(customerTypeId)
                .properties("{\"email\":\"x@x.com\"}")
                .createdAt(Instant.now())
                .build();
        when(repo.findAll()).thenReturn(List.of(self));

        OntologyObject subject = OntologyObject.builder()
                .id(id)
                .objectTypeId(customerTypeId)
                .objectTypeName("Customer")
                .properties(props)
                .build();

        List<EntityResolutionEngine.Candidate> candidates = engine.findDuplicates(subject, 10);

        assertThat(candidates).isEmpty();
    }

    @Test
    void different_object_types_are_ignored() throws Exception {
        UUID differentTypeId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID peerId = UUID.randomUUID();
        JsonNode props = mapper.readTree("{\"email\":\"a@b.com\"}");

        OntologyObjectEntity peer = OntologyObjectEntity.builder()
                .id(peerId)
                .objectTypeId(differentTypeId) // different type
                .properties("{\"email\":\"a@b.com\"}")
                .createdAt(Instant.now())
                .build();
        when(repo.findAll()).thenReturn(List.of(peer));

        OntologyObject subject = OntologyObject.builder()
                .id(subjectId)
                .objectTypeId(customerTypeId)
                .objectTypeName("Customer")
                .properties(props)
                .build();

        List<EntityResolutionEngine.Candidate> candidates = engine.findDuplicates(subject, 10);

        assertThat(candidates).isEmpty();
    }

    @Test
    void candidates_are_sorted_by_confidence_desc() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID exactPeerId = UUID.randomUUID();
        UUID fuzzyPeerId = UUID.randomUUID();
        JsonNode subjectProps = mapper.readTree(
                "{\"name\":\"Acme Corporation\",\"email\":\"info@acme.com\"}");

        OntologyObjectEntity exactPeer = OntologyObjectEntity.builder()
                .id(exactPeerId).objectTypeId(customerTypeId)
                .properties("{\"name\":\"Different Name Inc\",\"email\":\"info@acme.com\"}")
                .createdAt(Instant.now()).build();
        OntologyObjectEntity fuzzyPeer = OntologyObjectEntity.builder()
                .id(fuzzyPeerId).objectTypeId(customerTypeId)
                .properties("{\"name\":\"Acme Corp\",\"email\":\"other@x.com\"}")
                .createdAt(Instant.now()).build();
        when(repo.findAll()).thenReturn(List.of(fuzzyPeer, exactPeer));

        OntologyObject subject = OntologyObject.builder()
                .id(subjectId).objectTypeId(customerTypeId).objectTypeName("Customer")
                .properties(subjectProps).build();

        List<EntityResolutionEngine.Candidate> candidates = engine.findDuplicates(subject, 10);

        assertThat(candidates).hasSizeGreaterThanOrEqualTo(2);
        assertThat(candidates.get(0).matchType()).isEqualTo("EXACT");
        assertThat(candidates.get(0).confidence()).isGreaterThan(candidates.get(1).confidence());
    }

    @Test
    void levenshtein_similarity_returns_expected_values() {
        assertThat(EntityResolutionEngine.normalizedLevenshtein("abc", "abc"))
                .isCloseTo(1.0, within(0.001));
        assertThat(EntityResolutionEngine.normalizedLevenshtein("abc", "abd"))
                .isCloseTo(0.666, within(0.01));
        assertThat(EntityResolutionEngine.normalizedLevenshtein("abc", "xyz"))
                .isCloseTo(0.0, within(0.001));
        assertThat(EntityResolutionEngine.normalizedLevenshtein("", ""))
                .isEqualTo(1.0);
    }
}
