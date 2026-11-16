package com.supplychain.productservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores the physical-fingerprint embedding and its on-chain anchoring metadata.
 *
 * The {@code embedding} column is a pgvector {@code vector(128)} column.
 * Hibernate's native VECTOR mapping reads and writes the float array.
 */
@Entity
@Table(name = "product_fingerprints")
public class ProductFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false, unique = true)
    private String productId;

    /**
     * 128-dimensional float vector stored in a pgvector column.
     * The JPA repository uses native SQL for vector-distance queries.
     */
    @Column(name = "embedding", columnDefinition = "vector(128)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    @Column(name = "feature_hash", nullable = false, length = 64)
    private String featureHash;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "transaction_hash", length = 66)
    private String transactionHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Constructors ──────────────────────────────────────────────────────────

    public ProductFingerprint() {}

    public ProductFingerprint(UUID tenantId, String productId, float[] embedding, String featureHash, String metadata) {
        this.tenantId = tenantId;
        this.productId = productId;
        this.embedding = embedding;
        this.featureHash = featureHash;
        this.metadata = metadata;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public String getFeatureHash() { return featureHash; }
    public void setFeatureHash(String featureHash) { this.featureHash = featureHash; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long blockNumber) { this.blockNumber = blockNumber; }

    public String getTransactionHash() { return transactionHash; }
    public void setTransactionHash(String transactionHash) { this.transactionHash = transactionHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
