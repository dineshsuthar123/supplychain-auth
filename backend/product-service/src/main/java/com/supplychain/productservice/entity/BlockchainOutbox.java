package com.supplychain.productservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional outbox for exactly-once on-chain writes.
 *
 * A record is inserted in the same DB transaction as ProductFingerprint.
 * A background scheduler ({@link com.supplychain.productservice.blockchain.BlockchainOutboxProcessor})
 * picks PENDING records and publishes them to the Polygon network.
 */
@Entity
@Table(name = "blockchain_outbox")
public class BlockchainOutbox {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "feature_hash", nullable = false, length = 64)
    private String featureHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public BlockchainOutbox() {}

    public BlockchainOutbox(UUID tenantId, String productId, String featureHash) {
        this.tenantId = tenantId;
        this.productId = productId;
        this.featureHash = featureHash;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getFeatureHash() { return featureHash; }
    public void setFeatureHash(String featureHash) { this.featureHash = featureHash; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
