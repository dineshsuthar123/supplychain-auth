package com.supplychain.productservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable, database-backed audit event for a completed fingerprint check. */
@Entity
@Table(name = "verification_events")
public class VerificationEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "product_id", nullable = false) private String productId;
    @Column(nullable = false) private boolean verified;
    @Column(nullable = false) private double similarity;
    @Column(name = "blockchain_confirmed", nullable = false) private boolean blockchainConfirmed;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();

    protected VerificationEvent() { }
    public VerificationEvent(UUID tenantId, String productId, boolean verified, double similarity, boolean blockchainConfirmed) {
        this.tenantId = tenantId;
        this.productId = productId; this.verified = verified; this.similarity = similarity; this.blockchainConfirmed = blockchainConfirmed;
    }
    public String getProductId() { return productId; }
    public UUID getTenantId() { return tenantId; }
    public boolean isVerified() { return verified; }
    public double getSimilarity() { return similarity; }
    public boolean isBlockchainConfirmed() { return blockchainConfirmed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
