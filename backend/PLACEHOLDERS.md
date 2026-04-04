# NEXO Ontology Engine - Placeholders

## Fix 05: ONNX Runtime Embedding Pipeline

**Status:** Ready to implement.

The ONNX-based embedding pipeline (replacing the current tokenizer-only approach in
`EmbeddingService.java`) is deferred because downloading the ONNX model
(`sentence-transformers/all-MiniLM-L6-v2`) is too heavy for this session.

**To implement:**
1. Add `ai.djl.onnxruntime:onnxruntime` dependency to `pom.xml`.
2. Create `EmbeddingPipeline` that loads the ONNX model via DJL.
3. Update `EmbeddingService.embed()` to run full transformer inference instead of
   the tokenizer-only or hash-based fallback.
4. The `VectorType` Hibernate custom type (Fix 06) and pgvector column are already in place.
