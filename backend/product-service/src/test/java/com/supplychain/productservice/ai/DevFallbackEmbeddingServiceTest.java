package com.supplychain.productservice.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DevFallbackEmbeddingServiceTest {

    private final DevFallbackEmbeddingService service = new DevFallbackEmbeddingService();

    @Test
    void getEmbedding_returns128Dims() {
        float[] emb = service.getEmbedding("hello world".getBytes());
        assertThat(emb).hasSize(128);
    }

    @Test
    void getEmbedding_isL2Normalised() {
        float[] emb = service.getEmbedding("test image".getBytes());
        double norm = 0.0;
        for (float f : emb) norm += (double) f * f;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, within(0.001));
    }

    @Test
    void getEmbedding_isDeterministic() {
        byte[] img = "the same bytes".getBytes();
        float[] a = service.getEmbedding(img);
        float[] b = service.getEmbedding(img);
        assertThat(a).containsExactly(b);
    }

    @Test
    void getEmbedding_differsByInput() {
        float[] a = service.getEmbedding("image-one".getBytes());
        float[] b = service.getEmbedding("image-two".getBytes());
        // They won't be identical
        boolean different = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) { different = true; break; }
        }
        assertThat(different).isTrue();
    }

    private static org.assertj.core.data.Offset<Double> within(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }
}
