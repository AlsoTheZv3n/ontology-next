package com.nexoai.ontology.core.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.DataSourceDefinitionEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.SyncResultLogEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaDataSourceRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaSyncResultLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncScheduler {

    private final JpaDataSourceRepository dataSourceRepository;
    private final JpaSyncResultLogRepository syncResultLogRepository;
    private final SyncJob syncJob;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60_000)
    public void checkAndRunPendingSyncs() {
        var activeSources = dataSourceRepository.findByIsActiveTrue();

        for (DataSourceDefinitionEntity source : activeSources) {
            try {
                ConnectorType type = ConnectorType.valueOf(source.getConnectorType());
                JsonNode rawConfig = objectMapper.readTree(source.getConfig());
                Map<String, String> columnMapping = source.getColumnMapping() != null
                        ? objectMapper.readValue(source.getColumnMapping(), new TypeReference<>() {})
                        : Map.of();

                SyncResult result = syncJob.sync(source.getId(), type, rawConfig,
                        columnMapping, source.getObjectTypeId());

                // Save sync result log
                syncResultLogRepository.save(SyncResultLogEntity.builder()
                        .dataSourceId(source.getId())
                        .status(result.getStatus())
                        .objectsSynced(result.objectsSynced())
                        .objectsCreated(result.getObjectsCreated())
                        .objectsUpdated(result.getObjectsUpdated())
                        .objectsFailed(result.getObjectsFailed())
                        .errorMessage(result.getErrorMessage())
                        .startedAt(result.getStartedAt())
                        .finishedAt(result.getFinishedAt())
                        .build());

                // Update last synced timestamp
                source.setLastSyncedAt(result.getFinishedAt());
                dataSourceRepository.save(source);

            } catch (Exception e) {
                log.error("Scheduled sync failed for {}: {}", source.getApiName(), e.getMessage());
            }
        }
    }
}
