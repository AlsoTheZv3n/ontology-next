package com.nexoai.ontology.core.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaDataSourceRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.config.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        JsonNode payload = objectMapper.readTree(data);

        log.info("Processing CDC event: op={}, source_table={}, record={}", operation, sourceTable, record.getId());

        // Look up a data source definition that maps this source_table
        var dataSourceOpt = sourceTable.isBlank()
                ? java.util.Optional.<com.nexoai.ontology.adapters.out.persistence.entity.DataSourceDefinitionEntity>empty()
                : dataSourceRepository.findBySourceTable(sourceTable);

        if (dataSourceOpt.isPresent()) {
            var dataSource = dataSourceOpt.get();
            String externalId = payload.has("id") ? payload.get("id").asText() : null;

            if (externalId != null && dataSource.getObjectTypeId() != null) {
                if ("d".equalsIgnoreCase(operation)) {
                    // Delete
                    objectRepository.findByExternalIdAndDataSourceId(externalId, dataSource.getId())
                            .ifPresent(obj -> {
                                objectRepository.delete(obj);
                                log.info("CDC deleted object externalId={}", externalId);
                            });
                } else {
                    // Upsert (create or update)
                    var existing = objectRepository.findByExternalIdAndDataSourceId(externalId, dataSource.getId());
                    if (existing.isPresent()) {
                        var entity = existing.get();
                        entity.setProperties(payload.toString());
                        entity.setUpdatedAt(Instant.now());
                        objectRepository.save(entity);
                        log.info("CDC updated object externalId={}", externalId);
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
                    }
                }
            }
        }

        // Publish to WebSocket for live frontend updates
        wsPublisher.broadcastChange(ObjectChangeEvent.builder()
                .operation(operation.toUpperCase())
                .timestamp(Instant.now())
                .build());
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
