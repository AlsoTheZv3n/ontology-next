package com.nexoai.ontology.core.ml;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for EmbeddingService.
 *
 * Unit tests (always run): verify fallback, dimensions, normalization.
 * Integration tests (enabled with -Dnexo.ml.test.real=true): download real ONNX model,
 *   verify semantic similarity actually works.
 */
class EmbeddingServiceTest {

    private static EmbeddingService service;

    @BeforeAll
    static void setUp() {
        service = new EmbeddingService(new SimpleMeterRegistry());
        service.init();
    }

    @Test
    void embed_empty_string_returns_zero_vector() {
        float[] v = service.embed("");
        assertThat(v).hasSize(EmbeddingService.EMBEDDING_DIMENSION);
        for (float f : v) assertThat(f).isZero();
    }

    @Test
    void embed_null_returns_zero_vector() {
        float[] v = service.embed(null);
        assertThat(v).hasSize(EmbeddingService.EMBEDDING_DIMENSION);
    }

    @Test
    void embed_returns_correct_dimension() {
        float[] v = service.embed("hello world");
        assertThat(v).hasSize(EmbeddingService.EMBEDDING_DIMENSION);
    }

    @Test
    void embed_produces_normalized_vector() {
        float[] v = service.embed("the quick brown fox");
        double norm = 0;
        for (float f : v) norm += f * f;
        norm = Math.sqrt(norm);
        // Both real model and fallback produce normalized vectors
        assertThat(norm).isCloseTo(1.0, within(0.01));
    }

    @Test
    void embed_is_deterministic() {
        float[] v1 = service.embed("some text");
        float[] v2 = service.embed("some text");
        assertThat(v1).containsExactly(v2);
    }

    @Test
    void different_texts_produce_different_embeddings() {
        float[] a = service.embed("customers with high revenue");
        float[] b = service.embed("pizza with pepperoni");
        assertThat(a).isNotEqualTo(b);
    }

    /**
     * Integration test: verifies that the REAL model (when loaded) produces
     * semantically meaningful similarity scores. Only runs when real model is available.
     */
    @Test
    @EnabledIfSystemProperty(named = "nexo.ml.test.real", matches = "true")
    void real_model_produces_high_similarity_for_similar_texts() {
        if (!service.isRealModelAvailable()) {
            return; // skip if model not loaded
        }
        float[] a = service.embed("high revenue enterprise customers");
        float[] b = service.embed("wealthy corporate clients");
        float sim = cosine(a, b);
        // all-MiniLM-L6-v2 typically produces 0.5-0.7 for paraphrases
        // (compared to ~0.08 with previous pseudo-embeddings)
        assertThat(sim).as("similar texts should have high cosine similarity").isGreaterThan(0.5f);
    }

    @Test
    @EnabledIfSystemProperty(named = "nexo.ml.test.real", matches = "true")
    void real_model_produces_low_similarity_for_unrelated_texts() {
        if (!service.isRealModelAvailable()) {
            return;
        }
        float[] a = service.embed("high revenue customers");
        float[] b = service.embed("pizza topping pepperoni");
        float sim = cosine(a, b);
        assertThat(sim).as("unrelated texts should have low cosine similarity").isLessThan(0.4f);
    }

    private static float cosine(float[] a, float[] b) {
        float dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // vectors are already normalized
    }
}
