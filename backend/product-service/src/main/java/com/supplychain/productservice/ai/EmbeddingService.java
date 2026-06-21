package com.supplychain.productservice.ai;

/**
 * Common interface for embedding extraction – allows swapping the real ONNX model
 * for the dev-profile deterministic fallback without changing call-sites.
 */
public interface EmbeddingService {

    /**
     * Converts raw image bytes into a 128-dimensional feature vector.
     *
     * @param imageBytes raw JPEG or PNG image bytes
     * @return L2-normalised float[128]
     */
    float[] getEmbedding(byte[] imageBytes);
}
