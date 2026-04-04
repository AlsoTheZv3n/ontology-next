package com.nexoai.ontology.adapters.out.connector;

import com.jayway.jsonpath.JsonPath;
import com.nexoai.ontology.core.connector.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestApiConnector implements DataSourceConnector {

    private final WebClient.Builder webClientBuilder;

    @Override
    public ConnectorType getType() { return ConnectorType.REST_API; }

    @Override
    public ConnectionTestResult testConnection(ConnectorConfig config) {
        try {
            String url = config.get("url");
            WebClient client = buildClient(config);
            client.get().retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            return ConnectionTestResult.success("Successfully connected to: " + url);
        } catch (Exception e) {
            return ConnectionTestResult.failed("REST connection failed: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawRecord> fetchRecords(ConnectorConfig config) {
        String url = config.get("url");
        String dataPath = config.getOrDefault("dataPath", "$");
        String idField = config.get("idField");

        try {
            WebClient client = buildClient(config);
            String response = client.get()
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            Object parsed = JsonPath.read(response, dataPath);
            List<Map<String, Object>> items;

            if (parsed instanceof List) {
                items = (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                items = List.of((Map<String, Object>) parsed);
            } else {
                throw new ConnectorException("Unexpected data format at path: " + dataPath);
            }

            log.info("REST fetched {} records from {}", items.size(), url);
            return items.stream()
                    .map(item -> {
                        String extId = idField != null && item.get(idField) != null
                                ? item.get(idField).toString()
                                : UUID.randomUUID().toString();
                        return new RawRecord(item, extId);
                    })
                    .toList();
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException("REST fetch failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ColumnDefinition> introspectSchema(ConnectorConfig config) {
        List<RawRecord> sample = fetchRecords(config);
        if (sample.isEmpty()) return List.of();

        return sample.get(0).fields().entrySet().stream()
                .map(e -> ColumnDefinition.builder()
                        .name(e.getKey())
                        .dataType(e.getValue() != null ? e.getValue().getClass().getSimpleName() : "String")
                        .nullable(true)
                        .build())
                .toList();
    }

    private WebClient buildClient(ConnectorConfig config) {
        WebClient.Builder builder = webClientBuilder.clone().baseUrl(config.get("url"));
        String authToken = config.get("authToken");
        if (authToken != null && !authToken.isEmpty()) {
            builder.defaultHeader("Authorization", "Bearer " + authToken);
        }
        return builder.build();
    }
}
