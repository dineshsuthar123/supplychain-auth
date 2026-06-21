package com.supplychain.productservice.dto;

/**
 * Result of verifying a physical product fingerprint.
 *
 * {@code verified} is {@code true} only when:
 *   1. A matching embedding is found in the DB (cosine similarity >= threshold), AND
 *   2. The on-chain hash matches (blockchainConfirmed = true).
 *
 * If the blockchain is unreachable (circuit open), {@code blockchainConfirmed} is
 * {@code false} and {@code warning} explains the degraded mode.
 */
public record VerifyResponse(
        boolean verified,
        /** Cosine similarity score in [0, 1]; null if no candidate found. */
        Double confidence,
        boolean blockchainConfirmed,
        String transactionHash,
        Long blockNumber,
        /** Non-null when blockchain is unavailable or result is ambiguous. */
        String warning
) {
    public static VerifyResponse notFound() {
        return new VerifyResponse(false, null, false, null, null, "No fingerprint on record for this product");
    }

    public static VerifyResponse belowThreshold(double confidence) {
        return new VerifyResponse(false, confidence, false, null, null,
                "Similarity %.3f below verification threshold".formatted(confidence));
    }
}
