package com.nexoai.ontology.core.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.adapters.out.persistence.entity.OntologyObjectEntity;
import com.nexoai.ontology.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Async pipeline that generates embeddings for ontology objects
 * that don't have one yet. Integrates with SyncJob.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingPipeline {

    private final EmbeddingService embeddingService;
    private final JpaOntologyObjectRepository objectRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Generate embeddings for all objects of a given type that lack one.
     * Runs asynchronously to not block the sync process.
     */
    @Async
    public void generateMissingEmbeddings(UUID objectTypeId) {
        // Use string cast for UUID compatibility with JDBC template
        List<UUID> needsEmbedding = jdbcTemplate.queryForList(
                "SELECT id FROM ontology_objects WHERE object_type_id = ?::uuid AND embedding IS NULL",
                UUID.class, objectTypeId.toString()
        );

        if (needsEmbedding.isEmpty()) {
            log.info("All objects of type {} already have embeddings", objectTypeId);
            return;
        }

        log.info("Generating embeddings for {} objects of type {}", needsEmbedding.size(), objectTypeId);
        int success = 0, failed = 0;

        for (UUID objectId : needsEmbedding) {
            try {
                var entity = objectRepository.findById(objectId).orElse(null);
                if (entity == null) continue;

                var properties = objectMapper.readTree(entity.getProperties());
                float[] embedding = embeddingService.embedObject(properties);

                updateEmbedding(objectId, embedding);
                success++;
            } catch (Exception e) {
                log.warn("Failed to embed object {}: {}", objectId, e.getMessage());
                failed++;
            }
        }

        log.info("Embedding generation complete: {} success, {} failed", success, failed);
    }

    /**
     * Generate embedding for a single object.
     */
    @Async
    public void generateEmbedding(UUID objectId) {
        try {
            var entity = objectRepository.findById(objectId).orElse(null);
            if (entity == null) return;

            var properties = objectMapper.readTree(entity.getProperties());
            float[] embedding = embeddingService.embedObject(properties);
            updateEmbedding(objectId, embedding);
        } catch (Exception e) {
            log.warn("Failed to embed object {}: {}", objectId, e.getMessage());
        }
    }

    private void updateEmbedding(UUID objectId, float[] embedding) {
        String vectorStr = arrayToVectorString(embedding);
        jdbcTemplate.update(
                "UPDATE ontology_objects SET embedding = CAST(? AS vector), embedding_model = ?, embedded_at = NOW() WHERE id = CAST(? AS uuid)",
                vectorStr, embeddingService.getModelName(), objectId.toString()
        );
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
}
