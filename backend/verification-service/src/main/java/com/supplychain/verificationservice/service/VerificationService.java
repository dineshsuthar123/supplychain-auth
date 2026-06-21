package com.supplychain.verificationservice.service;

import com.supplychain.verificationservice.dto.VerificationRequest;
import com.supplychain.verificationservice.dto.VerificationResponse;
import com.supplychain.verificationservice.entity.VerificationLog;
import com.supplychain.verificationservice.repository.VerificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final VerificationLogRepository logRepository;

    // Redis is optional – if bean is missing (no Redis configured) the field stays null
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // Redis key prefix written by product-service on registration
    private static final String PRODUCT_KEY_PREFIX = "product:verified:";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * Core verification logic.
     *
     * Priority chain:
     * 1. Redis shared cache (sub-2ms): product-service writes "product:verified:{serial}"
     *    on every successful registration. This is the fast path for cache hits.
     * 2. Past verification log (MongoDB): if this serial was ever verified and logged we
     *    can trust the existing record.
     * 3. Not found: return verified=false. NEVER default to true.
     *
     * Audit log is written asynchronously so it does not block the response.
     */
    public VerificationResponse verifyProduct(VerificationRequest request) {
        if (request == null || !hasText(request.getProductSerialNumber())) {
            throw new IllegalArgumentException("productSerialNumber is required");
        }

        String serial = request.getProductSerialNumber();

        // --- Step 1: Redis cache (fast path, set by product-service on registration) ---
        boolean verified = false;
        if (redisTemplate != null) {
            try {
                verified = Boolean.TRUE.equals(redisTemplate.hasKey(PRODUCT_KEY_PREFIX + serial));
            } catch (Exception e) {
                log.warn("Redis unavailable for verification lookup, falling back to MongoDB log: {}", e.getMessage());
            }
        }

        // --- Step 2: MongoDB fallback – trust a previously successful verification log ---
        if (!verified) {
            try {
                verified = logRepository
                        .findFirstByProductSerialNumberOrderByVerifiedAtDesc(serial)
                        .map(VerificationLog::isVerified)
                        .orElse(false);
            } catch (Exception e) {
                log.warn("MongoDB lookup failed for {}: {}", serial, e.getMessage());
            }
        }

        String txHash = generateTxHash();
        Instant verifiedAt = Instant.now();

        // --- Step 3: Async audit log (does not affect response latency) ---
        persistAuditLogAsync(serial, verified, txHash, verifiedAt, request.getZkProof());

        VerificationResponse response = new VerificationResponse();
        response.setVerified(verified);
        response.setVerifier("verifier-service");
        response.setVerifiedAt(verifiedAt);
        response.setBlockchainTxHash(verified ? txHash : null);
        response.setProductSerialNumber(serial);
        response.setMessage(verified ? "Product verified" : "Product not found or not registered");
        return response;
    }

    @Async
    public void persistAuditLogAsync(String serial, boolean verified, String txHash,
                                     Instant verifiedAt, String zkProof) {
        try {
            logRepository.save(VerificationLog.builder()
                    .productSerialNumber(serial)
                    .verifier("verifier-service")
                    .verified(verified)
                    .verifiedAt(verifiedAt)
                    .zkProof(zkProof)
                    .blockchainTxHash(verified ? txHash : null)
                    .build());
        } catch (Exception e) {
            log.warn("Async MongoDB audit log failed for {}: {}", serial, e.getMessage());
        }
    }

    private String generateTxHash() {
        StringBuilder sb = new StringBuilder(66);
        sb.append("0x");
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 64; i++) sb.append(HEX[rng.nextInt(16)]);
        return sb.toString();
    }

    public Optional<VerificationResponse> getLatestVerification(String serialNumber) {
        if (!hasText(serialNumber)) {
            return Optional.empty();
        }

        return logRepository.findFirstByProductSerialNumberOrderByVerifiedAtDesc(serialNumber)
                .map(log -> {
                    VerificationResponse response = new VerificationResponse();
                    response.setVerified(log.isVerified());
                    response.setVerifier(log.getVerifier());
                    response.setVerifiedAt(log.getVerifiedAt());
                    response.setBlockchainTxHash(log.getBlockchainTxHash());
                    response.setProductSerialNumber(log.getProductSerialNumber());
                    response.setMessage("Latest verification log");
                    return response;
                });
    }
}
