package com.supplychain.productservice.repository;

import com.supplychain.productservice.entity.ProductFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface ProductFingerprintRepository extends JpaRepository<ProductFingerprint, UUID> {

    Optional<ProductFingerprint> findByTenantIdAndProductId(UUID tenantId, String productId);

    boolean existsByTenantIdAndProductId(UUID tenantId, String productId);

    long countByCreatedAtAfter(LocalDateTime time);

    List<ProductFingerprint> findTop10ByOrderByCreatedAtDesc();

    /**
     * Cosine-distance nearest-neighbour search using pgvector's {@code <=>} operator.
     *
     * Returns the closest fingerprint to the query vector for the given productId.
     * The result is a projection that includes the similarity score
     * (1 - cosine_distance so that 1.0 = identical).
     *
     * NOTE: The {@code CAST(:queryVector AS vector)} ensures the JDBC String
     * parameter is interpreted as a pgvector literal like {@code '[0.1,0.2,...]'}.
     */
    @Query(value = """
            SELECT pf.feature_hash, pf.transaction_hash, pf.block_number,
                   (1 - (pf.embedding <=> CAST(:queryVector AS vector))) AS similarity
            FROM product_fingerprints pf
            WHERE pf.tenant_id = :tenantId AND pf.product_id = :productId
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findVerificationCandidate(@Param("tenantId") UUID tenantId,
                                             @Param("productId") String productId,
                                             @Param("queryVector") String queryVector);

    /**
     * Audit-log projection deliberately excludes the pgvector column.  The
     * endpoint only needs attestation metadata, and hydrating the embedding
     * through Hibernate's array mapper is both unnecessary and driver-specific.
     */
    @Query(value = """
            SELECT pf.product_id, pf.feature_hash, pf.transaction_hash, pf.block_number, pf.created_at
            FROM product_fingerprints pf
            WHERE pf.tenant_id = :tenantId AND pf.product_id = :productId
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findEnrollmentLog(@Param("tenantId") UUID tenantId,
                                     @Param("productId") String productId);

    @Modifying
    @Query("UPDATE ProductFingerprint pf SET pf.blockNumber = :blockNumber, pf.transactionHash = :txHash WHERE pf.tenantId = :tenantId AND pf.productId = :productId")
    void updateOnChainInfo(@Param("tenantId") UUID tenantId, @Param("productId") String productId,
                           @Param("blockNumber") long blockNumber,
                           @Param("txHash") String txHash);
}
