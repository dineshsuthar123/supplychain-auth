package com.supplychain.productservice.exception;

/**
 * Thrown when a product fingerprint enrollment is attempted for a productId
 * that already exists in the database (idempotency guard).
 */
public class DuplicateProductException extends RuntimeException {

    private final String productId;

    public DuplicateProductException(String productId) {
        super("Product already enrolled: " + productId);
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}
