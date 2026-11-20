package com.supplychain.productservice.entity;

import com.supplychain.productservice.dto.VerifyResponse;
import com.supplychain.productservice.dto.VerifyResponse.Decision;
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
    @Enumerated(EnumType.STRING) @Column(length = 24) private Decision decision;
    @Column(columnDefinition = "text") private String warning;
    @Column(name = "transaction_hash", length = 66) private String transactionHash;
    @Column(name = "block_number") private Long blockNumber;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();

    protected VerificationEvent() { }

    public VerificationEvent(UUID tenantId, String productId, VerifyResponse response) {
        this.tenantId = tenantId;
        this.productId = productId;
        this.verified = response.verified();
        this.similarity = response.confidence() == null ? 0d : response.confidence();
        this.blockchainConfirmed = response.blockchainConfirmed();
        this.decision = response.decision();
        this.warning = response.warning();
        this.transactionHash = response.transactionHash();
        this.blockNumber = response.blockNumber();
    }

    public VerifyResponse toResponse() {
        // Older events used zero for missing scores. A failed zero-score record is ambiguous.
        Double score = decision == Decision.UNKNOWN_PRODUCT || (decision == null && !verified && similarity == 0)
                ? null : similarity;
        return new VerifyResponse(verified, score, blockchainConfirmed, transactionHash, blockNumber, warning,
                decision, productId, id, createdAt);
    }
}
