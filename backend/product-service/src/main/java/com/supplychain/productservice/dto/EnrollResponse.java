package com.supplychain.productservice.dto;

/**
 * Response returned immediately after enrollment.
 *
 * The blockchain write is asynchronous (transactional outbox pattern), so
 * {@code status} is initially {@code "PENDING"} and will become {@code "CONFIRMED"}
 * once the outbox processor successfully submits the transaction.
 */
public record EnrollResponse(
        String productId,
        String featureHash,
        /** "PENDING" or "CONFIRMED" */
        String status,
        /** Populated only after on-chain confirmation (null when PENDING). */
        String transactionHash,
        Long blockNumber
) {
    public static EnrollResponse pending(String productId, String featureHash) {
        return new EnrollResponse(productId, featureHash, "PENDING", null, null);
    }
}
