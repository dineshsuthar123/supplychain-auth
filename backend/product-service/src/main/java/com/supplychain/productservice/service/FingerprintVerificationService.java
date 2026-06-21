package com.supplychain.productservice.service;

import com.supplychain.productservice.blockchain.BlockchainService;
import com.supplychain.productservice.dto.VerifyRequest;
import com.supplychain.productservice.dto.VerifyResponse;
import com.supplychain.productservice.entity.VerificationEvent;
import com.supplychain.productservice.repository.ProductFingerprintRepository;
import com.supplychain.productservice.repository.VerificationEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import com.supplychain.productservice.tenant.TenantContext;

/**
 * Database-backed product verification. The normal request path reads the
 * persisted attestation state instead of synchronously waiting on a public
 * blockchain RPC. Ledger reconciliation remains asynchronous through the
 * transactional outbox; an optional strict mode is available for exceptional
 * workflows that require a live RPC response.
 */
@Service
public class FingerprintVerificationService {
    @Value("${supplyprint.verify.similarity-threshold:0.85}")
    private double similarityThreshold;

    @Value("${supplyprint.verify.require-blockchain:false}")
    private boolean requireLiveBlockchain;

    private final ProductFingerprintRepository fingerprintRepo;
    private final VerificationEventRepository verificationEventRepo;
    private final BlockchainService blockchainService;
    private final Counter verifyCounter;
    private final Timer vectorSearchTimer;
    private final Timer auditWriteTimer;
    private final Timer totalTimer;
    private final MeterRegistry meterRegistry;

    public FingerprintVerificationService(ProductFingerprintRepository fingerprintRepo,
                                          VerificationEventRepository verificationEventRepo,
                                          BlockchainService blockchainService,
                                          MeterRegistry meterRegistry) {
        this.fingerprintRepo = fingerprintRepo;
        this.verificationEventRepo = verificationEventRepo;
        this.blockchainService = blockchainService;
        this.meterRegistry = meterRegistry;
        this.verifyCounter = Counter.builder("supplyprint.verifications.total")
                .description("Total fingerprint verification attempts").register(meterRegistry);
        this.vectorSearchTimer = Timer.builder("verification.db.lookup.duration")
                .description("PostgreSQL pgvector similarity query latency").register(meterRegistry);
        this.auditWriteTimer = Timer.builder("verification.audit.write.duration").register(meterRegistry);
        this.totalTimer = Timer.builder("verification.total.duration").register(meterRegistry);
    }

    @Transactional
    public VerifyResponse verify(VerifyRequest request) {
        Timer.Sample total = Timer.start(meterRegistry);
        try {
        verifyCounter.increment();
        UUID tenantId = TenantContext.getRequired();
        String vector = toPgVectorLiteral(request.embedding());
        List<Object[]> rows = vectorSearchTimer.record(() ->
                fingerprintRepo.findVerificationCandidate(tenantId, request.productId(), vector));
        if (rows == null || rows.isEmpty()) return persist(tenantId, request.productId(), VerifyResponse.notFound());

        Object[] row = rows.get(0);
        double similarity = ((Number) row[3]).doubleValue();
        if (similarity < similarityThreshold) return persist(tenantId, request.productId(), VerifyResponse.belowThreshold(similarity));

        String featureHash = (String) row[0];
        String transactionHash = (String) row[1];
        Long blockNumber = row[2] == null ? null : ((Number) row[2]).longValue();

        boolean persistedAttestation = transactionHash != null && blockNumber != null;
        if (!requireLiveBlockchain) {
            String warning = persistedAttestation ? null : "Ledger attestation is pending; database identity match succeeded";
            return persist(tenantId, request.productId(), new VerifyResponse(true, similarity, persistedAttestation,
                    transactionHash, blockNumber, warning));
        }
        try {
            boolean chainMatches = blockchainService.verifyOnChain(request.productId(), featureHash);
            if (!chainMatches) return persist(tenantId, request.productId(), new VerifyResponse(false, similarity, false,
                    null, null, "Ledger hash mismatch"));
            return persist(tenantId, request.productId(), new VerifyResponse(true, similarity, true,
                    transactionHash, blockNumber, null));
        } catch (Exception ignored) {
            return persist(tenantId, request.productId(), new VerifyResponse(true, similarity, persistedAttestation,
                    transactionHash, blockNumber, "Live ledger check unavailable; returned persisted attestation state"));
        }
        } finally { total.stop(totalTimer); }
    }

    private VerifyResponse persist(UUID tenantId, String productId, VerifyResponse response) {
        auditWriteTimer.record(() -> verificationEventRepo.save(new VerificationEvent(tenantId, productId, response.verified(),
                response.confidence() == null ? 0d : response.confidence(), response.blockchainConfirmed())));
        meterRegistry.counter("verification.result.count", "result", response.verified() ? "verified" : "rejected").increment();
        return response;
    }

    private static String toPgVectorLiteral(List<Double> embedding) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) value.append(',');
            Double item = embedding.get(i);
            if (item == null || !Double.isFinite(item)) throw new IllegalArgumentException("embedding contains a non-finite value");
            value.append(item.floatValue());
        }
        return value.append(']').toString();
    }
}
