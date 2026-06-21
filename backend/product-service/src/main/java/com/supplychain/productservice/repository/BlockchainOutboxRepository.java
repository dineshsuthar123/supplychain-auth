package com.supplychain.productservice.repository;

import com.supplychain.productservice.entity.BlockchainOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlockchainOutboxRepository extends JpaRepository<BlockchainOutbox, UUID> {

    List<BlockchainOutbox> findByStatusOrderByCreatedAtAsc(BlockchainOutbox.Status status);

    long countByStatus(BlockchainOutbox.Status status);

    /**
     * Fetch pending records that have been attempted fewer than {@code maxAttempts} times,
     * to avoid hammering the RPC with permanently failing records.
     */
    @Query("SELECT o FROM BlockchainOutbox o WHERE o.status = 'PENDING' AND o.attempts < :maxAttempts ORDER BY o.createdAt ASC")
    List<BlockchainOutbox> findEligiblePending(@Param("maxAttempts") int maxAttempts);

    @Modifying
    @Query("DELETE FROM BlockchainOutbox o WHERE o.tenantId = :tenantId AND o.productId = :productId AND o.status = 'SENT'")
    void deleteSentByTenantIdAndProductId(@Param("tenantId") UUID tenantId, @Param("productId") String productId);
}
