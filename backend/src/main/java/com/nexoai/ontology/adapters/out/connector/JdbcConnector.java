package com.nexoai.ontology.adapters.out.connector;

import com.nexoai.ontology.core.connector.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

@Component
@Slf4j
public class JdbcConnector implements DataSourceConnector {

    @Override
    public ConnectorType getType() { return ConnectorType.JDBC; }

    @Override
    public ConnectionTestResult testConnection(ConnectorConfig config) {
        try (Connection conn = DriverManager.getConnection(
                config.get("url"), config.get("username"), config.get("password"))) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            return ConnectionTestResult.success("Connected to: " + dbName + " " + conn.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            return ConnectionTestResult.failed("Connection failed: " + e.getMessage());
        }
    }

    @Override
    public List<RawRecord> fetchRecords(ConnectorConfig config) {
        String url = config.get("url");
        String username = config.get("username");
        String password = config.get("password");
        String query = config.get("query");
        String idColumn = config.get("idColumn");

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            List<RawRecord> records = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> fields = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    fields.put(meta.getColumnName(i), rs.getObject(i));
                }
                String externalId = idColumn != null ? String.valueOf(rs.getObject(idColumn)) : UUID.randomUUID().toString();
                records.add(new RawRecord(fields, externalId));
            }

            log.info("JDBC fetched {} records from {}", records.size(), url);
            return records;
        } catch (SQLException e) {
            throw new ConnectorException("JDBC fetch failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ColumnDefinition> introspectSchema(ConnectorConfig config) {
        String url = config.get("url");
        String table = config.get("table");
        if (table == null) table = config.get("query");

        try (Connection conn = DriverManager.getConnection(
                url, config.get("username"), config.get("password"))) {

            DatabaseMetaData dbMeta = conn.getMetaData();
            ResultSet cols = dbMeta.getColumns(null, null, table, null);
            List<ColumnDefinition> columns = new ArrayList<>();

            while (cols.next()) {
                columns.add(ColumnDefinition.builder()
                        .name(cols.getString("COLUMN_NAME"))
                        .dataType(cols.getString("TYPE_NAME"))
                        .nullable(cols.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
                        .build());
            }

            if (columns.isEmpty()) {
                // Fallback: try query-based introspection
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT 0")) {
                    ResultSetMetaData rsMeta = rs.getMetaData();
                    for (int i = 1; i <= rsMeta.getColumnCount(); i++) {
                        columns.add(ColumnDefinition.builder()
                                .name(rsMeta.getColumnName(i))
                                .dataType(rsMeta.getColumnTypeName(i))
                                .nullable(rsMeta.isNullable(i) == ResultSetMetaData.columnNullable)
                                .build());
                    }
                }
            }
            return columns;
        } catch (SQLException e) {
            throw new ConnectorException("JDBC introspection failed: " + e.getMessage(), e);
        }
    }
}
