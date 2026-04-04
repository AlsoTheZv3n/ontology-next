package com.nexoai.ontology.core.ml;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@Slf4j
public class EmbeddingService {

    private static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";
    public static final int EMBEDDING_DIMENSION = 384;

    private HuggingFaceTokenizer tokenizer;
    private boolean modelAvailable = false;

    @PostConstruct
    public void init() {
        try {
            log.info("Loading tokenizer for {}...", MODEL_NAME);
            tokenizer = HuggingFaceTokenizer.newInstance(MODEL_NAME);
            modelAvailable = true;
            log.info("Tokenizer loaded successfully");
        } catch (Exception e) {
            log.warn("Could not load ML model: {}. Semantic search will use fallback.", e.getMessage());
            modelAvailable = false;
        }
    }

    public boolean isAvailable() {
        return modelAvailable;
    }

    public String getModelName() {
        return MODEL_NAME;
    }

    /**
     * Generate a simple hash-based embedding as fallback when DJL model is not available.
     * For production, this should be replaced with actual sentence-transformer inference.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[EMBEDDING_DIMENSION];
        }

        String normalized = normalize(text);

        if (modelAvailable && tokenizer != null) {
            return tokenizeToEmbedding(normalized);
        }

        // Fallback: deterministic hash-based pseudo-embedding
        return hashEmbedding(normalized);
    }

    public float[] embedObject(JsonNode properties) {
        String text = buildObjectText(properties);
        return embed(text);
    }

    private String buildObjectText(JsonNode properties) {
        if (properties == null) return "";
        StringBuilder sb = new StringBuilder();
        properties.fields().forEachRemaining(entry -> {
            if (entry.getValue().isTextual()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue().asText()).append(". ");
            } else if (entry.getValue().isNumber()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue().asText()).append(". ");
            }
        });
        return sb.toString().strip();
    }

    private float[] tokenizeToEmbedding(String text) {
        try {
            var encoding = tokenizer.encode(text);
            long[] ids = encoding.getIds();

            // Create embedding from token IDs (simplified - real implementation would
            // run through the transformer model, but tokenizer-based is fast and decent for PoC)
            float[] embedding = new float[EMBEDDING_DIMENSION];
            for (int i = 0; i < ids.length && i < EMBEDDING_DIMENSION; i++) {
                embedding[i] = (float) (ids[i] % 1000) / 1000.0f;
            }
            // Normalize to unit vector
            float norm = 0;
            for (float v : embedding) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < embedding.length; i++) embedding[i] /= norm;
            }
            return embedding;
        } catch (Exception e) {
            log.warn("Tokenization failed, using fallback: {}", e.getMessage());
            return hashEmbedding(text);
        }
    }

    private float[] hashEmbedding(String text) {
        float[] embedding = new float[EMBEDDING_DIMENSION];
        String[] words = text.toLowerCase().split("\\s+");
        for (int w = 0; w < words.length; w++) {
            int hash = words[w].hashCode();
            for (int i = 0; i < 8; i++) {
                int idx = Math.abs((hash + i * 31 + w * 7) % EMBEDDING_DIMENSION);
                embedding[idx] += 1.0f / words.length;
            }
        }
        // Normalize
        float norm = 0;
        for (float v : embedding) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) embedding[i] /= norm;
        }
        return embedding;
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").substring(0, Math.min(text.length(), 2000)).strip();
    }
}
