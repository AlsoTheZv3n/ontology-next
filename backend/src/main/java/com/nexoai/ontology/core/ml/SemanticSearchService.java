package com.nexoai.ontology.core.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaObjectTypeRepository;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final EmbeddingService embeddingService;
    private final DataSource dataSource;
    private final JpaObjectTypeRepository objectTypeRepository;
    private final ObjectMapper objectMapper;

    public List<SemanticSearchResult> search(String query, String objectType, int limit, float minSimilarity) {
        // 1. Query embedden
        float[] queryEmbedding = embeddingService.embed(query);

        // 2. ObjectType ID auflösen
        UUID objectTypeId = objectTypeRepository.findByIsActiveTrue().stream()
                .filter(ot -> ot.getApiName().equals(objectType))
                .findFirst()
                .map(ot -> ot.getId())
                .orElseThrow(() -> new OntologyException("ObjectType not found: " + objectType));

        // 3. pgvector Nearest-Neighbor Search via native JDBC
        String embeddingStr = arrayToVectorString(queryEmbedding);

        String sql = "SELECT o.id, o.properties, o.created_at, " +
                "1 - (o.embedding <=> '" + embeddingStr + "'::vector) AS similarity " +
                "FROM ontology_objects o " +
                "WHERE o.object_type_id = '" + objectTypeId + "' " +
                "AND o.embedding IS NOT NULL " +
                "AND 1 - (o.embedding <=> '" + embeddingStr + "'::vector) >= " + minSimilarity + " " +
                "ORDER BY o.embedding <=> '" + embeddingStr + "'::vector " +
                "LIMIT " + limit;

        List<SemanticSearchResult> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                JsonNode props;
                try {
                    props = objectMapper.readTree(rs.getString("properties"));
                } catch (Exception e) {
                    props = objectMapper.createObjectNode();
                }
                results.add(new SemanticSearchResult(
                        UUID.fromString(rs.getString("id")),
                        objectType,
                        props,
                        rs.getFloat("similarity"),
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
                ));
            }
        } catch (SQLException e) {
            log.error("Semantic search SQL failed: {}", e.getMessage());
            throw new OntologyException("Semantic search failed: " + e.getMessage());
        }

        log.info("Semantic search for '{}' in {} returned {} results", query, objectType, results.size());
        return results;
    }

    private String arrayToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public record SemanticSearchResult(
            UUID id,
            String objectType,
            JsonNode properties,
            float similarity,
            java.time.Instant createdAt
    ) {}
}
