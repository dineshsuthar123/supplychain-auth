package com.supplychain.productservice.repository;

import com.supplychain.productservice.entity.VerificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface VerificationEventRepository extends JpaRepository<VerificationEvent, UUID> {
    long countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime time);
    long countByTenantIdAndVerifiedFalseAndCreatedAtAfter(UUID tenantId, LocalDateTime time);
    List<VerificationEvent> findTop10ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
