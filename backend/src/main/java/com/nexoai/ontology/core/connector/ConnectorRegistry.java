package com.nexoai.ontology.core.connector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ConnectorRegistry {

    private final Map<ConnectorType, DataSourceConnector> connectors;

    public ConnectorRegistry(List<DataSourceConnector> connectorList) {
        this.connectors = connectorList.stream()
                .collect(Collectors.toMap(DataSourceConnector::getType, Function.identity()));
        log.info("Registered {} connectors: {}", connectors.size(), connectors.keySet());
    }

    public DataSourceConnector getConnector(ConnectorType type) {
        DataSourceConnector connector = connectors.get(type);
        if (connector == null) {
            throw new ConnectorException("No connector registered for type: " + type);
        }
        return connector;
    }
}
