package com.nexoai.ontology.core.ml;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/**
 * Translator for sentence-transformers models (e.g., all-MiniLM-L6-v2).
 * Input: String
 * Output: float[384] normalized embedding
 *
 * Pipeline:
 * 1. Tokenize input (BERT-style: input_ids, attention_mask, token_type_ids)
 * 2. Run through ONNX transformer model
 * 3. Mean-pool token embeddings weighted by attention mask
 * 4. L2-normalize the resulting vector
 */
public class SentenceTransformerTranslator implements Translator<String, float[]> {

    private final HuggingFaceTokenizer tokenizer;

    public SentenceTransformerTranslator(HuggingFaceTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        Encoding enc = tokenizer.encode(input);
        NDManager m = ctx.getNDManager();
        NDArray inputIds = m.create(enc.getIds()).expandDims(0);
        NDArray attentionMask = m.create(enc.getAttentionMask()).expandDims(0);

        // Store attention mask for mean-pooling in processOutput
        ctx.setAttachment("attention_mask", attentionMask);

        // DJL PyTorch BERT expects only (input_ids, attention_mask)
        return new NDList(inputIds, attentionMask);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        NDArray tokenEmbeddings = list.get(0); // [1, seq_len, 384]
        NDArray attentionMask = (NDArray) ctx.getAttachment("attention_mask");

        // Mean pooling with attention mask
        NDArray mask = attentionMask.expandDims(-1).toType(DataType.FLOAT32, false);
        NDArray sum = tokenEmbeddings.mul(mask).sum(new int[]{1});
        NDArray count = mask.sum(new int[]{1}).clip(1e-9f, Float.MAX_VALUE);
        NDArray pooled = sum.div(count);

        // L2 normalization
        NDArray squareSum = pooled.pow(2).sum(new int[]{1});
        NDArray norm = squareSum.sqrt().clip(1e-9f, Float.MAX_VALUE);
        NDArray normalized = pooled.div(norm.expandDims(-1));

        return normalized.toFloatArray();
    }

    @Override
    public Batchifier getBatchifier() {
        return null; // We handle batching manually via expandDims
    }
}
