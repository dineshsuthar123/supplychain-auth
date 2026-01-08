package com.supplychain.common.repository;

import com.supplychain.common.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    
    Optional<Tenant> findByApiKeyHash(String apiKeyHash);
    
    Optional<Tenant> findBySlug(String slug);
    
    Optional<Tenant> findByStripeCustomerId(String stripeCustomerId);
    
    Optional<Tenant> findByStripeSubscriptionId(String stripeSubscriptionId);
    
    @Query("SELECT t FROM Tenant t WHERE t.active = true AND t.deleted = false")
    Iterable<Tenant> findAllActive();
    
    boolean existsBySlug(String slug);
    
    boolean existsByName(String name);
}
