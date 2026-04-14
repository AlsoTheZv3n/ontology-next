package com.nexoai.ontology.core.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.DataSourceDefinitionEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaDataSourceRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.config.websocket.WebSocketPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests CDC event routing without standing up Redis or Kafka.
 * Invokes processEvent() directly with simulated Debezium payloads.
 */
class CdcEventConsumerTest {

    private JpaDataSourceRepository dataSourceRepo;
    private JpaOntologyObjectRepository objectRepo;
    private WebSocketPublisher wsPublisher;
    private MeterRegistry meterRegistry;
    private CdcEventConsumer consumer;
    private ObjectMapper mapper;

    private UUID dsId;
    private UUID objectTypeId;
    private DataSourceDefinitionEntity ds;

    @BeforeEach
    void setUp() {
        dataSourceRepo = mock(JpaDataSourceRepository.class);
        objectRepo = mock(JpaOntologyObjectRepository.class);
        wsPublisher = mock(WebSocketPublisher.class);
        meterRegistry = new SimpleMeterRegistry();
        mapper = new ObjectMapper();

        consumer = new CdcEventConsumer(
                mock(RedisTemplate.class),
                mapper,
                mock(ConflictResolutionService.class),
                wsPublisher,
                mock(JdbcTemplate.class),
                dataSourceRepo,
                objectRepo,
                meterRegistry
        );

        dsId = UUID.randomUUID();
        objectTypeId = UUID.randomUUID();
        ds = DataSourceDefinitionEntity.builder()
                .id(dsId).objectTypeId(objectTypeId)
                .sourceTable("customers").build();
    }

    @Test
    void create_event_inserts_new_object() throws Exception {
        JsonNode payload = mapper.readTree("{\"id\":\"42\",\"name\":\"Acme\",\"email\":\"a@b.com\"}");
        when(dataSourceRepo.findBySourceTable("customers")).thenReturn(Optional.of(ds));
        when(objectRepo.findByExternalIdAndDataSourceId("42", dsId)).thenReturn(Optional.empty());

        consumer.processEvent("c", "customers", payload, System.currentTimeMillis() - 100);

        ArgumentCaptor<OntologyObjectEntity> cap = ArgumentCaptor.forClass(OntologyObjectEntity.class);
        verify(objectRepo).save(cap.capture());
        assertThat(cap.getValue().getExternalId()).isEqualTo("42");
        assertThat(cap.getValue().getObjectTypeId()).isEqualTo(objectTypeId);
        assertThat(cap.getValue().getDataSourceId()).isEqualTo(dsId);
        verify(wsPublisher).broadcastChange(any());
    }

    @Test
    void update_event_updates_existing_object() throws Exception {
        JsonNode payload = mapper.readTree("{\"id\":\"42\",\"name\":\"Acme Inc\"}");
        OntologyObjectEntity existing = OntologyObjectEntity.builder()
                .id(UUID.randomUUID()).objectTypeId(objectTypeId)
                .externalId("42").dataSourceId(dsId)
                .properties("{\"id\":\"42\",\"name\":\"Old\"}")
                .createdAt(Instant.now()).build();
        when(dataSourceRepo.findBySourceTable("customers")).thenReturn(Optional.of(ds));
        when(objectRepo.findByExternalIdAndDataSourceId("42", dsId)).thenReturn(Optional.of(existing));

        consumer.processEvent("u", "customers", payload, System.currentTimeMillis());

        verify(objectRepo).save(existing);
        assertThat(existing.getProperties()).contains("Acme Inc");
    }

    @Test
    void delete_event_removes_existing_object() throws Exception {
        JsonNode payload = mapper.readTree("{\"id\":\"42\"}");
        OntologyObjectEntity existing = OntologyObjectEntity.builder()
                .id(UUID.randomUUID()).objectTypeId(objectTypeId)
                .externalId("42").dataSourceId(dsId).build();
        when(dataSourceRepo.findBySourceTable("customers")).thenReturn(Optional.of(ds));
        when(objectRepo.findByExternalIdAndDataSourceId("42", dsId)).thenReturn(Optional.of(existing));

        consumer.processEvent("d", "customers", payload, System.currentTimeMillis());

        verify(objectRepo).delete(existing);
        verify(objectRepo, never()).save(any());
    }

    @Test
    void unknown_source_table_is_ignored_but_still_broadcasts() throws Exception {
        JsonNode payload = mapper.readTree("{\"id\":\"1\"}");
        when(dataSourceRepo.findBySourceTable("orphans")).thenReturn(Optional.empty());

        consumer.processEvent("c", "orphans", payload, System.currentTimeMillis());

        verifyNoInteractions(objectRepo);
        verify(wsPublisher).broadcastChange(any());
    }

    @Test
    void lag_metric_records_delta_between_ts_and_processing_time() throws Exception {
        JsonNode payload = mapper.readTree("{\"id\":\"1\",\"name\":\"x\"}");
        when(dataSourceRepo.findBySourceTable("customers")).thenReturn(Optional.of(ds));
        when(objectRepo.findByExternalIdAndDataSourceId("1", dsId)).thenReturn(Optional.empty());

        long tsMs = System.currentTimeMillis() - 500;
        consumer.processEvent("c", "customers", payload, tsMs);

        var timer = meterRegistry.find("nexo.cdc.lag").tag("table", "customers").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(500);
    }

    @Test
    void null_payload_is_ignored_without_error() {
        // Should not throw and should not touch any repo.
        consumer.processEvent("c", "customers", null, System.currentTimeMillis());

        verifyNoInteractions(objectRepo);
        verifyNoInteractions(wsPublisher);
    }

    @Test
    void counter_incremented_per_operation_type() throws Exception {
        JsonNode createPayload = mapper.readTree("{\"id\":\"1\"}");
        JsonNode deletePayload = mapper.readTree("{\"id\":\"2\"}");
        OntologyObjectEntity toDelete = OntologyObjectEntity.builder()
                .id(UUID.randomUUID()).externalId("2").dataSourceId(dsId).build();
        when(dataSourceRepo.findBySourceTable("customers")).thenReturn(Optional.of(ds));
        when(objectRepo.findByExternalIdAndDataSourceId("1", dsId)).thenReturn(Optional.empty());
        when(objectRepo.findByExternalIdAndDataSourceId("2", dsId)).thenReturn(Optional.of(toDelete));

        consumer.processEvent("c", "customers", createPayload, 0);
        consumer.processEvent("d", "customers", deletePayload, 0);

        assertThat(meterRegistry.counter("nexo.cdc.events.processed", "op", "c").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("nexo.cdc.events.processed", "op", "d").count()).isEqualTo(1);
    }
}
