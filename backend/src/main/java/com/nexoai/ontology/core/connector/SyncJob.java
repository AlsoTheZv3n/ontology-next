package com.nexoai.ontology.core.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import com.nexoai.ontology.core.domain.object.OntologyObject;
import com.nexoai.ontology.core.ml.EmbeddingPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncJob {

    private final ConnectorRegistry connectorRegistry;
    private final RecordMapper recordMapper;
    private final JpaOntologyObjectRepository objectRepository;
    private final EmbeddingPipeline embeddingPipeline;

    @Transactional
    public SyncResult sync(UUID dataSourceId, ConnectorType connectorType, JsonNode rawConfig,
                           Map<String, String> columnMapping, UUID objectTypeId) {
        log.info("Starting sync for datasource: {}", dataSourceId);
        Instant startedAt = Instant.now();

        DataSourceConnector connector = connectorRegistry.getConnector(connectorType);
        ConnectorConfig config = ConnectorConfig.builder()
                .dataSourceId(dataSourceId)
                .type(connectorType)
                .rawConfig(rawConfig)
                .columnMapping(columnMapping)
                .targetObjectTypeId(objectTypeId)
                .build();

        int created = 0, updated = 0, failed = 0;

        try {
            List<RawRecord> records = connector.fetchRecords(config);

            for (RawRecord record : records) {
                try {
                    OntologyObject mapped = recordMapper.toOntologyObject(record, columnMapping, objectTypeId);
                    JsonNode props = mapped.getProperties();

                    var existing = objectRepository.findByExternalIdAndDataSourceId(
                            record.externalId(), dataSourceId);

                    if (existing.isPresent()) {
                        OntologyObjectEntity entity = existing.get();
                        entity.setProperties(props.toString());
                        objectRepository.save(entity);
                        updated++;
                    } else {
                        var now = Instant.now();
                        OntologyObjectEntity entity = OntologyObjectEntity.builder()
                                .objectTypeId(objectTypeId)
                                .properties(props.toString())
                                .externalId(record.externalId())
                                .dataSourceId(dataSourceId)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
                        objectRepository.save(entity);
                        created++;
                    }
                } catch (Exception e) {
                    log.error("Failed to sync record {}: {}", record.externalId(), e.getMessage());
                    failed++;
                }
            }

            log.info("Sync complete for {}: created={}, updated={}, failed={}", dataSourceId, created, updated, failed);

            // Async: generate embeddings for synced objects
            embeddingPipeline.generateMissingEmbeddings(objectTypeId);

            return SyncResult.success(created, updated, failed, startedAt);

        } catch (ConnectorException e) {
            log.error("Sync failed for {}: {}", dataSourceId, e.getMessage());
            return SyncResult.failed(e.getMessage(), startedAt);
        }
    }
}
