package com.supplychain.productservice.service;

import com.supplychain.productservice.dto.EnrollRequest;
import com.supplychain.productservice.dto.EnrollResponse;
import com.supplychain.productservice.entity.BlockchainOutbox;
import com.supplychain.productservice.entity.ProductFingerprint;
import com.supplychain.productservice.exception.DuplicateProductException;
import com.supplychain.productservice.repository.BlockchainOutboxRepository;
import com.supplychain.productservice.repository.ProductFingerprintRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import com.supplychain.productservice.tenant.TenantContext;

/**
 * Core enrollment logic.
 *
 * <p>Enrollment flow:
 * <ol>
 *   <li>Validate 128-dim embedding.</li>
 *   <li>Compute featureHash = SHA-256(productId + floatArrayToString(embedding)).</li>
 *   <li>In a single DB transaction: insert ProductFingerprint + BlockchainOutbox(PENDING).</li>
 *   <li>Return immediately with status=PENDING – blockchain write is async.</li>
 * </ol>
 */
@Service
public class FingerprintEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(FingerprintEnrollmentService.class);

    private final ProductFingerprintRepository fingerprintRepo;
    private final BlockchainOutboxRepository   outboxRepo;
    private final Counter                      enrollCounter;
    private final MeterRegistry                meterRegistry;
    private final Timer                        enrollmentTimer;

    public FingerprintEnrollmentService(ProductFingerprintRepository fingerprintRepo,
                                        BlockchainOutboxRepository outboxRepo,
                                        MeterRegistry meterRegistry) {
        this.fingerprintRepo = fingerprintRepo;
        this.outboxRepo      = outboxRepo;
        this.meterRegistry   = meterRegistry;
        this.enrollCounter   = Counter.builder("supplyprint.enrollments.total")
                                      .description("Total product fingerprint enrollments")
                                      .register(meterRegistry);
        this.enrollmentTimer = Timer.builder("enrollment.total.duration").register(meterRegistry);
    }

    /**
     * Enrolls a product fingerprint.
     *
     * @throws DuplicateProductException if productId already enrolled
     */
    @Transactional
    public EnrollResponse enroll(EnrollRequest request) {
        Timer.Sample total = Timer.start(meterRegistry);
        try {
        UUID tenantId = TenantContext.getRequired();
        if (fingerprintRepo.existsByTenantIdAndProductId(tenantId, request.productId())) {
            throw new DuplicateProductException(request.productId());
        }

        float[] embeddingArray = toFloatArray(request.embedding());
        String  featureHash    = computeFeatureHash(request.productId(), request.embedding());

        ProductFingerprint fingerprint = new ProductFingerprint(
                tenantId, request.productId(),
                embeddingArray,
                featureHash,
                request.metadata()
        );
        fingerprintRepo.save(fingerprint);

        // Transactional outbox – processed asynchronously by BlockchainOutboxProcessor
        outboxRepo.save(new BlockchainOutbox(tenantId, request.productId(), featureHash));

        enrollCounter.increment();
        log.info("Enrolled productId={} featureHash={}", request.productId(), featureHash);

        return EnrollResponse.pending(request.productId(), featureHash);
        } finally { total.stop(enrollmentTimer); }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * SHA-256(productId + Arrays.toString(embedding)).
     * The exact same logic must be used on the blockchain outbox processor side.
     */
    public static String computeFeatureHash(String productId, List<Double> embedding) {
        try {
            String input = productId + embedding.toString();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                                         .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static float[] toFloatArray(List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).floatValue();
        }
        return arr;
    }
}
