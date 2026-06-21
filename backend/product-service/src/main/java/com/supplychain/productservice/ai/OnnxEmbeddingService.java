package com.supplychain.productservice.ai;

import ai.onnxruntime.*;
import com.supplychain.productservice.exception.ModelNotAvailableException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/**
 * Loads the {@code fingerprint.onnx} model at startup and performs in-process
 * inference to produce 128-dimensional L2-normalised feature vectors from a
 * 256×256 grayscale image patch.
 *
 * <p>The model file must be present at:
 * {@code classpath:models/fingerprint.onnx}
 *
 * <p>This bean is active for all Spring profiles <em>except</em> {@code dev}.
 * In the {@code dev} profile, {@link DevFallbackEmbeddingService} is used instead.
 */
@Service
@Profile("!dev")
public class OnnxEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingService.class);

    private static final String MODEL_PATH = "models/fingerprint.onnx";
    private static final int    IMG_SIZE   = 256;
    private static final int    EMBED_DIM  = 128;

    @Value("${supplyprint.onnx.inter-op-threads:1}")
    private int interOpThreads;

    @Value("${supplyprint.onnx.intra-op-threads:2}")
    private int intraOpThreads;

    private OrtEnvironment environment;
    private OrtSession     session;
    private final Timer decodeTimer;
    private final Timer inferenceTimer;
    private final Timer normalizationTimer;

    public OnnxEmbeddingService(MeterRegistry meterRegistry) {
        this.decodeTimer = Timer.builder("image.decode.duration").register(meterRegistry);
        this.inferenceTimer = Timer.builder("onnx.inference.duration").register(meterRegistry);
        this.normalizationTimer = Timer.builder("embedding.normalization.duration").register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource(MODEL_PATH);
            if (!resource.exists()) {
                throw new ModelNotAvailableException(
                        "ONNX model not found at classpath:" + MODEL_PATH +
                        ". Run ml/train_and_export.py first.");
            }
            byte[] modelBytes;
            try (InputStream is = resource.getInputStream()) {
                modelBytes = is.readAllBytes();
            }
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setInterOpNumThreads(interOpThreads);
            opts.setIntraOpNumThreads(intraOpThreads);
            session = environment.createSession(modelBytes, opts);
            log.info("OnnxEmbeddingService: model loaded successfully ({} bytes)", modelBytes.length);
        } catch (OrtException | IOException e) {
            throw new ModelNotAvailableException("Failed to initialise ONNX session", e);
        }
    }

    /**
     * Converts raw image bytes to a 128-dim float embedding.
     *
     * @param imageBytes JPEG or PNG image bytes
     * @return normalised 128-dim float array
     * @throws ModelNotAvailableException if inference fails
     */
    @Override
    public float[] getEmbedding(byte[] imageBytes) {
        if (session == null) {
            throw new ModelNotAvailableException("ONNX session is not initialised");
        }
        try {
            float[] pixelData = decodeTimer.record(() -> preprocessImage(imageBytes));
            // Shape: [1, 1, 256, 256]
            long[] shape = {1L, 1L, IMG_SIZE, IMG_SIZE};
            Timer.Sample inference = Timer.start();
            float[] output;
            try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(pixelData), shape);
                 OrtSession.Result result = session.run(Collections.singletonMap("image_patch", tensor))) {
                output = ((float[][]) result.get(0).getValue())[0];
            } finally { inference.stop(inferenceTimer); }
            return normalizationTimer.record(() -> normalise(output));
        } catch (OrtException e) {
            throw new ModelNotAvailableException("ONNX inference failed", e);
        }
    }

    private static float[] normalise(float[] values) {
        double sum = 0; for (float value : values) sum += value * value;
        double norm = Math.sqrt(sum); if (norm == 0) throw new ModelNotAvailableException("Model returned zero embedding");
        float[] normalised = values.clone(); for (int i = 0; i < normalised.length; i++) normalised[i] /= (float) norm;
        return normalised;
    }

    /**
     * Resizes image to 256×256 grayscale and normalises pixels to [0, 1].
     */
    private float[] preprocessImage(byte[] imageBytes) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (src == null) {
                throw new ModelNotAvailableException("Could not decode image bytes");
            }
            // Resize to 256×256
            BufferedImage resized = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, IMG_SIZE, IMG_SIZE, null);
            g.dispose();

            // Extract grayscale pixel values and normalise to [0, 1]
            float[] pixels = new float[IMG_SIZE * IMG_SIZE];
            int[] raw = resized.getRaster().getPixels(0, 0, IMG_SIZE, IMG_SIZE, (int[]) null);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = raw[i] / 255.0f;
            }
            return pixels;
        } catch (IOException e) {
            throw new ModelNotAvailableException("Image preprocessing failed", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session     != null) session.close();
            if (environment != null) environment.close();
        } catch (OrtException e) {
            log.warn("Failed to cleanly close ONNX session: {}", e.getMessage());
        }
    }
}
