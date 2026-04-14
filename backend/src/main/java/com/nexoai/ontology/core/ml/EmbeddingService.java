package com.nexoai.ontology.core.ml;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Real transformer-based embedding service using sentence-transformers/all-MiniLM-L6-v2 (384-dim).
 *
 * Three loading strategies (in order):
 * 1. Local ONNX file at `nexo.ml.model-path` (for production with pre-downloaded model)
 * 2. HuggingFace Hub via DJL ONNX model zoo (auto-download first run)
 * 3. Fallback: deterministic hash-based pseudo-embedding (only when `nexo.ml.fail-fast=false`)
 *
 * Embeddings are mean-pooled and L2-normalized, so cosine similarity = dot product.
 */
@Service
@Slf4j
public class EmbeddingService {

    private static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";
    public static final int EMBEDDING_DIMENSION = 384;

    @Value("${nexo.ml.model-path:}")
    private String modelPath;

    @Value("${nexo.ml.fail-fast:false}")
    private boolean failFast;

    private final MeterRegistry meterRegistry;

    private HuggingFaceTokenizer tokenizer;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private Timer embedTimer;
    private boolean realModelAvailable = false;

    public EmbeddingService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        embedTimer = Timer.builder("nexo.embedding.latency")
                .description("Time to generate a single embedding")
                .register(meterRegistry);

        try {
            tokenizer = HuggingFaceTokenizer.newInstance(MODEL_NAME);
            log.info("Tokenizer loaded for {}", MODEL_NAME);
        } catch (Exception e) {
            log.error("Failed to load tokenizer", e);
            if (failFast) {
                throw new IllegalStateException("Tokenizer required", e);
            }
            return;
        }

        // Try to load real transformer model (local ONNX -> PyTorch from zoo -> fallback)
        try {
            model = loadModel();
            predictor = model.newPredictor();
            realModelAvailable = true;
            log.info("Real transformer model loaded successfully");

        } catch (Exception e) {
            log.warn("Could not load transformer model: {}", e.getMessage());
            if (failFast) {
                throw new IllegalStateException(
                        "Embedding model required for production (nexo.ml.fail-fast=true)", e);
            }
            log.warn("Falling back to hash-based pseudo-embeddings. NOT SUITABLE FOR PRODUCTION.");
            realModelAvailable = false;
        }
    }

    private ZooModel<String, float[]> loadModel() throws Exception {
        // Strategy 1: Local model directory (production - preferred)
        if (modelPath != null && !modelPath.isBlank()) {
            Path path = Paths.get(modelPath);
            log.info("Loading model from local path: {}", path);
            return Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(path)
                    .optEngine("PyTorch")
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .build()
                    .loadModel();
        }

        // Strategy 2: PyTorch model zoo with built-in sentence-transformers translator
        // (downloads from HuggingFace on first run, cached in ~/.djl.ai/)
        String url = "djl://ai.djl.huggingface.pytorch/" + MODEL_NAME;
        log.info("Loading PyTorch model from zoo: {}", url);
        return Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(url)
                .optEngine("PyTorch")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .build()
                .loadModel();
    }

    public boolean isAvailable() {
        return tokenizer != null;
    }

    public boolean isRealModelAvailable() {
        return realModelAvailable;
    }

    public String getModelName() {
        return MODEL_NAME;
    }

    /**
     * Generate a 384-dim L2-normalized embedding for the given text.
     * Uses real transformer if available, else falls back to hash-embedding.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[EMBEDDING_DIMENSION];
        }

        meterRegistry.counter("nexo.embeddings.total").increment();
        String normalized = normalize(text);

        if (realModelAvailable && predictor != null) {
            return embedTimer.record(() -> realEmbed(normalized));
        }

        // Fallback
        meterRegistry.counter("nexo.embeddings.fallback").increment();
        return hashEmbedding(normalized);
    }

    private float[] realEmbed(String text) {
        try {
            return predictor.predict(text);
        } catch (Exception e) {
            log.error("Embedding prediction failed for text of length {}: {}",
                    text.length(), e.getMessage());
            meterRegistry.counter("nexo.embeddings.errors").increment();
            throw new RuntimeException("Embedding failed", e);
        }
    }

    public float[] embedObject(JsonNode properties) {
        return embed(buildObjectText(properties));
    }

    private String buildObjectText(JsonNode properties) {
        if (properties == null) return "";
        StringBuilder sb = new StringBuilder();
        properties.fields().forEachRemaining(entry -> {
            if (entry.getValue().isTextual() || entry.getValue().isNumber()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue().asText()).append(". ");
            }
        });
        return sb.toString().strip();
    }

    /**
     * Deterministic hash-based pseudo-embedding. Last-resort fallback.
     * Produces normalized vectors but has NO semantic meaning — similarity scores
     * will be near-zero for any pair of texts.
     */
    private float[] hashEmbedding(String text) {
        float[] embedding = new float[EMBEDDING_DIMENSION];
        String[] words = text.toLowerCase().split("\\s+");
        for (int w = 0; w < words.length; w++) {
            int hash = words[w].hashCode();
            for (int i = 0; i < 8; i++) {
                int idx = Math.abs((hash + i * 31 + w * 7) % EMBEDDING_DIMENSION);
                embedding[idx] += 1.0f / Math.max(1, words.length);
            }
        }
        float norm = 0;
        for (float v : embedding) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) embedding[i] /= norm;
        }
        return embedding;
    }

    private String normalize(String text) {
        String trimmed = text.replaceAll("\\s+", " ").strip();
        return trimmed.substring(0, Math.min(trimmed.length(), 2000));
    }

    @PreDestroy
    public void shutdown() {
        if (predictor != null) {
            try { predictor.close(); } catch (Exception ignored) {}
        }
        if (model != null) {
            try { model.close(); } catch (Exception ignored) {}
        }
    }
}
