package com.supplychain.productservice.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * DEV-ONLY fallback: produces a deterministic pseudo-random 128-dim embedding
 * derived from the SHA-256 hash of the image bytes.  This lets the pipeline
 * be tested end-to-end without a real ONNX model present.
 *
 * <b>WARNING</b>: This service does NOT perform real computer-vision inference.
 * It is activated only when Spring profile {@code dev} is active.
 * Production must use {@link OnnxEmbeddingService}.
 */
@Service
@Profile("dev")
public class DevFallbackEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DevFallbackEmbeddingService.class);

    public DevFallbackEmbeddingService() {
        log.warn("*** DEV PROFILE: Using deterministic hash-based embedding – NOT real AI inference ***");
    }

    @Override
    public float[] getEmbedding(byte[] imageBytes) {
        byte[] digest = sha256(imageBytes);
        // Expand the 32-byte digest into 128 floats by repeating and perturbing
        float[] embedding = new float[128];
        for (int i = 0; i < 128; i++) {
            int  byteVal = digest[i % 32] & 0xFF;
            embedding[i] = (byteVal / 255.0f) * 2.0f - 1.0f; // [-1, 1]
        }
        return l2Normalise(embedding);
    }

    private static float[] l2Normalise(float[] v) {
        double norm = 0.0;
        for (float f : v) norm += (double) f * f;
        norm = Math.sqrt(norm);
        if (norm < 1e-9) return v;
        float[] out = Arrays.copyOf(v, v.length);
        for (int i = 0; i < out.length; i++) out[i] /= (float) norm;
        return out;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
