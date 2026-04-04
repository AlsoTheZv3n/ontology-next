package com.nexoai.ontology.core.connector;

import java.util.List;

public interface DataSourceConnector {
    ConnectorType getType();
    ConnectionTestResult testConnection(ConnectorConfig config);
    List<RawRecord> fetchRecords(ConnectorConfig config);
    List<ColumnDefinition> introspectSchema(ConnectorConfig config);
}
