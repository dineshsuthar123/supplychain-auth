package com.supplychain.productservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** A fingerprint decision and, after persistence, its audit identity. */
public record VerifyResponse(
        boolean verified,
        Double confidence,
        boolean blockchainConfirmed,
        String transactionHash,
        Long blockNumber,
        String warning,
        Decision decision,
        String productId,
        UUID attemptId,
        LocalDateTime createdAt
) {
    public enum Decision { MATCH, NO_MATCH, UNKNOWN_PRODUCT, LEDGER_MISMATCH }

    public VerifyResponse(Decision decision, Double confidence, boolean blockchainConfirmed,
                          String transactionHash, Long blockNumber, String warning) {
        this(decision == Decision.MATCH, confidence, blockchainConfirmed, transactionHash, blockNumber,
                warning, decision, null, null, null);
    }

    public VerifyResponse recordedAs(String productId, UUID attemptId, LocalDateTime createdAt) {
        return new VerifyResponse(verified, confidence, blockchainConfirmed, transactionHash, blockNumber, warning,
                decision, productId, attemptId, createdAt);
    }

    public static VerifyResponse notFound() {
        return new VerifyResponse(Decision.UNKNOWN_PRODUCT, null, false, null, null,
                "No fingerprint on record for this product");
    }

    public static VerifyResponse belowThreshold(double confidence) {
        return new VerifyResponse(Decision.NO_MATCH, confidence, false, null, null,
                "Similarity %.3f below verification threshold".formatted(confidence));
    }
}
