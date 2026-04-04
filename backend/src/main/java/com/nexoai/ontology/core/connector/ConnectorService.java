package com.nexoai.ontology.core.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.DataSourceDefinitionEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.SyncResultLogEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaDataSourceRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaSyncResultLogRepository;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConnectorService {

    private final JpaDataSourceRepository dataSourceRepository;
    private final JpaSyncResultLogRepository syncResultLogRepository;
    private final JpaObjectTypeRepository objectTypeRepository;
    private final ConnectorRegistry connectorRegistry;
    private final SyncJob syncJob;
    private final ObjectMapper objectMapper;

    public DataSourceDefinitionEntity createDataSource(String apiName, String displayName,
                                                        ConnectorType connectorType, String targetObjectType,
                                                        JsonNode config, Map<String, String> columnMapping,
                                                        String syncIntervalCron) {
        if (dataSourceRepository.existsByApiName(apiName)) {
            throw new OntologyException("DataSource already exists: " + apiName);
        }

        UUID objectTypeId = objectTypeRepository.findByIsActiveTrue().stream()
                .filter(ot -> ot.getApiName().equals(targetObjectType))
                .findFirst()
                .map(ot -> ot.getId())
                .orElseThrow(() -> new OntologyException("ObjectType not found: " + targetObjectType));

        DataSourceDefinitionEntity entity = DataSourceDefinitionEntity.builder()
                .apiName(apiName)
                .displayName(displayName)
                .connectorType(connectorType.name())
                .config(config.toString())
                .objectTypeId(objectTypeId)
                .columnMapping(objectMapper.valueToTree(columnMapping).toString())
                .syncIntervalCron(syncIntervalCron != null ? syncIntervalCron : "0 */15 * * * *")
                .build();

        return dataSourceRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<DataSourceDefinitionEntity> getAllDataSources() {
        return dataSourceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public DataSourceDefinitionEntity getDataSource(UUID id) {
        return dataSourceRepository.findById(id)
                .orElseThrow(() -> new OntologyException("DataSource not found: " + id));
    }

    public DataSourceDefinitionEntity updateDataSource(UUID id, JsonNode config,
                                                        Map<String, String> columnMapping,
                                                        String syncIntervalCron) {
        var entity = getDataSource(id);
        if (config != null) entity.setConfig(config.toString());
        if (columnMapping != null) entity.setColumnMapping(objectMapper.valueToTree(columnMapping).toString());
        if (syncIntervalCron != null) entity.setSyncIntervalCron(syncIntervalCron);
        return dataSourceRepository.save(entity);
    }

    public void deleteDataSource(UUID id) {
        dataSourceRepository.deleteById(id);
    }

    public ConnectionTestResult testConnection(UUID id) {
        var entity = getDataSource(id);
        ConnectorType type = ConnectorType.valueOf(entity.getConnectorType());
        DataSourceConnector connector = connectorRegistry.getConnector(type);

        try {
            JsonNode rawConfig = objectMapper.readTree(entity.getConfig());
            ConnectorConfig config = ConnectorConfig.builder()
                    .dataSourceId(id).type(type).rawConfig(rawConfig)
                    .targetObjectTypeId(entity.getObjectTypeId()).build();
            return connector.testConnection(config);
        } catch (Exception e) {
            return ConnectionTestResult.failed(e.getMessage());
        }
    }

    public SyncResult triggerSync(UUID id) {
        var entity = getDataSource(id);
        ConnectorType type = ConnectorType.valueOf(entity.getConnectorType());

        try {
            JsonNode rawConfig = objectMapper.readTree(entity.getConfig());
            Map<String, String> columnMapping = entity.getColumnMapping() != null
                    ? objectMapper.readValue(entity.getColumnMapping(), new TypeReference<>() {})
                    : Map.of();

            SyncResult result = syncJob.sync(id, type, rawConfig, columnMapping, entity.getObjectTypeId());

            syncResultLogRepository.save(SyncResultLogEntity.builder()
                    .dataSourceId(id)
                    .status(result.getStatus())
                    .objectsSynced(result.objectsSynced())
                    .objectsCreated(result.getObjectsCreated())
                    .objectsUpdated(result.getObjectsUpdated())
                    .objectsFailed(result.getObjectsFailed())
                    .errorMessage(result.getErrorMessage())
                    .startedAt(result.getStartedAt())
                    .finishedAt(result.getFinishedAt())
                    .build());

            entity.setLastSyncedAt(result.getFinishedAt());
            dataSourceRepository.save(entity);

            return result;
        } catch (Exception e) {
            throw new OntologyException("Sync trigger failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<SyncResultLogEntity> getSyncLog(UUID dataSourceId, int limit) {
        return syncResultLogRepository.findByDataSourceIdOrderByStartedAtDesc(
                dataSourceId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<ColumnDefinition> introspectSchema(UUID id) {
        var entity = getDataSource(id);
        ConnectorType type = ConnectorType.valueOf(entity.getConnectorType());
        DataSourceConnector connector = connectorRegistry.getConnector(type);

        try {
            JsonNode rawConfig = objectMapper.readTree(entity.getConfig());
            ConnectorConfig config = ConnectorConfig.builder()
                    .dataSourceId(id).type(type).rawConfig(rawConfig)
                    .targetObjectTypeId(entity.getObjectTypeId()).build();
            return connector.introspectSchema(config);
        } catch (Exception e) {
            throw new OntologyException("Schema introspection failed: " + e.getMessage());
        }
    }
}
