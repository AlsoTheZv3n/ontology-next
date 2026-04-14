package com.nexoai.ontology.core.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaDataSourceRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.config.websocket.WebSocketPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Consumes CDC events from Redis Streams.
 * Only active when Redis is configured (nexo.cdc.enabled=true).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "nexo.cdc.enabled", havingValue = "true", matchIfMissing = false)
public class CdcEventConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConflictResolutionService conflictResolution;
    private final WebSocketPublisher wsPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final JpaDataSourceRepository dataSourceRepository;
    private final JpaOntologyObjectRepository objectRepository;
    private final MeterRegistry meterRegistry;

    private static final String STREAM_KEY = "ontology:cdc";
    private static final String GROUP_NAME = "nexo-group";
    private static final String CONSUMER_NAME = "consumer-1";

    @Scheduled(fixedDelay = 100)
    public void consumeEvents() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(100),
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) return;

            for (MapRecord<String, Object, Object> record : records) {
                try {
                    processRecord(record);
                    redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("Failed to process CDC event {}: {}", record.getId(), e.getMessage());
                    moveToDeadLetterQueue(record, e);
                    redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                }
            }
        } catch (Exception e) {
            // Redis not available or stream doesn't exist yet -- silently skip
            log.trace("CDC consumer poll skipped: {}", e.getMessage());
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record) throws Exception {
        Map<Object, Object> values = record.getValue();
        String operation = String.valueOf(values.getOrDefault("operation", "u"));
        String data = String.valueOf(values.getOrDefault("data", "{}"));
        String sourceTable = String.valueOf(values.getOrDefault("source_table", ""));
        long tsMs = parseLong(values.get("ts_ms"));
        JsonNode payload = objectMapper.readTree(data);

        processEvent(operation, sourceTable, payload, tsMs);
    }

    /**
     * Core CDC processing logic — split out so unit tests can exercise it
     * without standing up Redis. Accepts a Debezium-style operation code
     * (c/u/d) plus the source table and the row payload.
     *
     * @param operation  Debezium op code: c=create, u=update, d=delete
     * @param sourceTable source table name used to look up DataSourceDefinition
     * @param payload    row data (for "d" this is the "before" image)
     * @param tsMs       source-side event timestamp in ms (0 = unknown → skip lag)
     */
    public void processEvent(String operation, String sourceTable, JsonNode payload, long tsMs) {
        if (payload == null) {
            log.debug("CDC event ignored: null payload (op={}, table={})", operation, sourceTable);
            return;
        }
        log.info("Processing CDC event: op={}, source_table={}", operation, sourceTable);

        if (tsMs > 0 && meterRegistry != null) {
            long lagMs = Math.max(0, System.currentTimeMillis() - tsMs);
            meterRegistry.timer("nexo.cdc.lag", "table", sourceTable)
                    .record(Duration.ofMillis(lagMs));
        }

        var dataSourceOpt = sourceTable == null || sourceTable.isBlank()
                ? java.util.Optional.<com.nexoai.ontology.adapters.out.persistence.entity.DataSourceDefinitionEntity>empty()
                : dataSourceRepository.findBySourceTable(sourceTable);

        if (dataSourceOpt.isPresent()) {
            var dataSource = dataSourceOpt.get();
            String externalId = payload.has("id") ? payload.get("id").asText() : null;

            if (externalId != null && dataSource.getObjectTypeId() != null) {
                if ("d".equalsIgnoreCase(operation)) {
                    objectRepository.findByExternalIdAndDataSourceId(externalId, dataSource.getId())
                            .ifPresent(obj -> {
                                objectRepository.delete(obj);
                                log.info("CDC deleted object externalId={}", externalId);
                            });
                    incrementCounter("nexo.cdc.events.processed", "op", "d");
                } else {
                    var existing = objectRepository.findByExternalIdAndDataSourceId(externalId, dataSource.getId());
                    if (existing.isPresent()) {
                        var entity = existing.get();
                        entity.setProperties(payload.toString());
                        entity.setUpdatedAt(Instant.now());
                        objectRepository.save(entity);
                        log.info("CDC updated object externalId={}", externalId);
                        incrementCounter("nexo.cdc.events.processed", "op", "u");
                    } else {
                        var entity = OntologyObjectEntity.builder()
                                .objectTypeId(dataSource.getObjectTypeId())
                                .externalId(externalId)
                                .dataSourceId(dataSource.getId())
                                .properties(payload.toString())
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                        objectRepository.save(entity);
                        log.info("CDC created object externalId={}", externalId);
                        incrementCounter("nexo.cdc.events.processed", "op", "c");
                    }
                }
            }
        }

        wsPublisher.broadcastChange(ObjectChangeEvent.builder()
                .operation(operation.toUpperCase())
                .timestamp(Instant.now())
                .build());
    }

    private void incrementCounter(String name, String... tags) {
        if (meterRegistry != null) meterRegistry.counter(name, tags).increment();
    }

    private static long parseLong(Object v) {
        if (v == null) return 0L;
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }

    private void moveToDeadLetterQueue(MapRecord<String, Object, Object> record, Exception error) {
        try {
            String eventData = objectMapper.writeValueAsString(record.getValue());
            jdbcTemplate.update(
                    "INSERT INTO cdc_dead_letter_queue (stream_key, record_id, event_data, error_message) VALUES (?, ?, ?::jsonb, ?)",
                    STREAM_KEY, record.getId().getValue(), eventData, error.getMessage()
            );
        } catch (Exception e) {
            log.error("Failed to write to DLQ: {}", e.getMessage());
        }
    }
}
