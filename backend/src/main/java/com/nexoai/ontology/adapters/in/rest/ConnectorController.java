package com.nexoai.ontology.adapters.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.DataSourceDefinitionEntity;
import com.nexoai.ontology.adapters.out.persistence.entity.SyncResultLogEntity;
import com.nexoai.ontology.core.connector.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorService connectorService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<DataSourceDefinitionEntity> create(@RequestBody Map<String, Object> request) {
        String apiName = (String) request.get("apiName");
        String displayName = (String) request.get("displayName");
        ConnectorType connectorType = ConnectorType.valueOf((String) request.get("connectorType"));
        String targetObjectType = (String) request.get("targetObjectType");
        JsonNode configNode = objectMapper.valueToTree(request.get("config"));
        Map<String, String> columnMapping = (Map<String, String>) request.get("columnMapping");
        String syncIntervalCron = (String) request.get("syncIntervalCron");

        var created = connectorService.createDataSource(apiName, displayName, connectorType,
                targetObjectType, configNode, columnMapping, syncIntervalCron);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<DataSourceDefinitionEntity>> getAll() {
        return ResponseEntity.ok(connectorService.getAllDataSources());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataSourceDefinitionEntity> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(connectorService.getDataSource(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        connectorService.deleteDataSource(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ConnectionTestResult> testConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(connectorService.testConnection(id));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<SyncResult> triggerSync(@PathVariable UUID id) {
        return ResponseEntity.ok(connectorService.triggerSync(id));
    }

    @GetMapping("/{id}/sync-log")
    public ResponseEntity<List<SyncResultLogEntity>> getSyncLog(@PathVariable UUID id,
                                                                 @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(connectorService.getSyncLog(id, limit));
    }

    @GetMapping("/{id}/schema")
    public ResponseEntity<List<ColumnDefinition>> introspectSchema(@PathVariable UUID id) {
        return ResponseEntity.ok(connectorService.introspectSchema(id));
    }
}
