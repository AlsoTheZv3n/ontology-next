package com.nexoai.ontology.adapters.out.connector;

import com.nexoai.ontology.core.connector.*;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.util.*;

@Component
@Slf4j
public class CsvConnector implements DataSourceConnector {

    @Override
    public ConnectorType getType() { return ConnectorType.CSV; }

    @Override
    public ConnectionTestResult testConnection(ConnectorConfig config) {
        String filePath = config.get("filePath");
        try {
            try (var reader = new FileReader(filePath)) {
                return ConnectionTestResult.success("File accessible: " + filePath);
            }
        } catch (Exception e) {
            return ConnectionTestResult.failed("Cannot access file: " + e.getMessage());
        }
    }

    @Override
    public List<RawRecord> fetchRecords(ConnectorConfig config) {
        String filePath = config.get("filePath");
        String delimiter = config.getOrDefault("delimiter", ",");
        String idColumn = config.get("idColumn");

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(filePath))
                .withCSVParser(new CSVParserBuilder()
                        .withSeparator(delimiter.charAt(0))
                        .build())
                .build()) {

            String[] headers = reader.readNext();
            if (headers == null) return List.of();

            int idIndex = -1;
            if (idColumn != null) {
                idIndex = Arrays.asList(headers).indexOf(idColumn);
            }

            List<RawRecord> records = new ArrayList<>();
            String[] row;
            while ((row = reader.readNext()) != null) {
                Map<String, Object> fields = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < row.length; i++) {
                    fields.put(headers[i].trim(), row[i]);
                }
                String externalId = idIndex >= 0 && idIndex < row.length
                        ? row[idIndex]
                        : UUID.randomUUID().toString();
                records.add(new RawRecord(fields, externalId));
            }

            log.info("CSV fetched {} records from {}", records.size(), filePath);
            return records;

        } catch (Exception e) {
            throw new ConnectorException("CSV read failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ColumnDefinition> introspectSchema(ConnectorConfig config) {
        String filePath = config.get("filePath");
        String delimiter = config.getOrDefault("delimiter", ",");

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(filePath))
                .withCSVParser(new CSVParserBuilder()
                        .withSeparator(delimiter.charAt(0))
                        .build())
                .build()) {

            String[] headers = reader.readNext();
            if (headers == null) return List.of();

            return Arrays.stream(headers)
                    .map(h -> ColumnDefinition.builder()
                            .name(h.trim())
                            .dataType("STRING")
                            .nullable(true)
                            .build())
                    .toList();
        } catch (Exception e) {
            throw new ConnectorException("CSV introspection failed: " + e.getMessage(), e);
        }
    }
}
