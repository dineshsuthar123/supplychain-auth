package com.supplychain.productservice.repository;

import com.supplychain.productservice.entity.VerificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface VerificationEventRepository extends JpaRepository<VerificationEvent, UUID> {
    Slice<VerificationEvent> findByTenantIdAndProductIdOrderByCreatedAtDescIdDesc(
            UUID tenantId, String productId, Pageable page);

    long countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime time);
    long countByTenantIdAndVerifiedFalseAndCreatedAtAfter(UUID tenantId, LocalDateTime time);
    List<VerificationEvent> findTop10ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
