package com.supplychain.common.service;

import com.supplychain.common.model.Role;
import com.supplychain.common.model.Tenant;
import com.supplychain.common.repository.RoleRepository;
import com.supplychain.common.repository.TenantRepository;
import com.supplychain.common.security.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing tenants (manufacturers/brands)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TenantService {
    
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final ApiKeyService apiKeyService;
    
    /**
     * Register a new tenant with initial setup
     */
    @Transactional
    public Tenant registerTenant(
            String name,
            String slug,
            String description,
            String industry,
            String country,
            Tenant.SubscriptionTier tier
    ) {
        // Validate slug uniqueness
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Slug already exists: " + slug);
        }
        
        // Generate API key
        String apiKey = apiKeyService.generateApiKey();
        String apiKeyHash = apiKeyService.hashApiKey(apiKey);
        String apiKeyPrefix = apiKey.substring(0, Math.min(12, apiKey.length()));
        
        // Create tenant
        Tenant tenant = Tenant.builder()
                .name(name)
                .slug(slug)
                .description(description)
                .industry(industry)
                .country(country)
                .subscriptionTier(tier)
                .subscriptionStatus(Tenant.SubscriptionStatus.TRIAL)
                .apiKeyHash(apiKeyHash)
                .apiKeyPrefix(apiKeyPrefix)
                .subscriptionStartDate(LocalDateTime.now())
                .subscriptionEndDate(LocalDateTime.now().plusDays(14)) // 14-day trial
                .build();
        
        // Apply tier-specific limits
        applyTierLimits(tenant, tier);
        
        tenant = tenantRepository.save(tenant);
        
        // Create default roles
        createDefaultRoles(tenant.getId());
        
        log.info("Registered new tenant: {} with ID: {}", name, tenant.getId());
        log.info("API Key (SAVE THIS - won't be shown again): {}", apiKey);
        
        return tenant;
    }
    
    /**
     * Create default roles for new tenant
     */
    private void createDefaultRoles(UUID tenantId) {
        Role adminRole = Role.createTenantAdminRole(tenantId);
        Role manufacturerRole = Role.createManufacturerRole(tenantId);
        Role auditorRole = Role.createAuditorRole(tenantId);
        
        roleRepository.save(adminRole);
        roleRepository.save(manufacturerRole);
        roleRepository.save(auditorRole);
        
        log.info("Created default roles for tenant: {}", tenantId);
    }
    
    /**
     * Apply resource limits based on subscription tier
     */
    private void applyTierLimits(Tenant tenant, Tenant.SubscriptionTier tier) {
        switch (tier) {
            case FREE:
                tenant.setMonthlyVerificationLimit(1000);
                tenant.setMonthlyRegistrationLimit(100);
                tenant.setMaxProducts(10000);
                tenant.setMaxUsers(5);
                tenant.setAnalyticsEnabled(false);
                tenant.setMultiChainEnabled(false);
                tenant.setIotIntegrationEnabled(false);
                tenant.setMlFraudDetectionEnabled(false);
                break;
                
            case STARTER:
                tenant.setMonthlyVerificationLimit(10000);
                tenant.setMonthlyRegistrationLimit(1000);
                tenant.setMaxProducts(50000);
                tenant.setMaxUsers(10);
                tenant.setAnalyticsEnabled(true);
                tenant.setMultiChainEnabled(false);
                tenant.setIotIntegrationEnabled(false);
                tenant.setMlFraudDetectionEnabled(false);
                break;
                
            case PROFESSIONAL:
                tenant.setMonthlyVerificationLimit(100000);
                tenant.setMonthlyRegistrationLimit(10000);
                tenant.setMaxProducts(500000);
                tenant.setMaxUsers(50);
                tenant.setAnalyticsEnabled(true);
                tenant.setMultiChainEnabled(true);
                tenant.setIotIntegrationEnabled(true);
                tenant.setMlFraudDetectionEnabled(true);
                break;
                
            case ENTERPRISE:
                tenant.setMonthlyVerificationLimit(Integer.MAX_VALUE);
                tenant.setMonthlyRegistrationLimit(Integer.MAX_VALUE);
                tenant.setMaxProducts(Integer.MAX_VALUE);
                tenant.setMaxUsers(Integer.MAX_VALUE);
                tenant.setAnalyticsEnabled(true);
                tenant.setMultiChainEnabled(true);
                tenant.setIotIntegrationEnabled(true);
                tenant.setMlFraudDetectionEnabled(true);
                break;
        }
    }
    
    /**
     * Update tenant subscription tier
     */
    @Transactional
    public Tenant updateSubscriptionTier(UUID tenantId, Tenant.SubscriptionTier newTier) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        
        tenant.setSubscriptionTier(newTier);
        applyTierLimits(tenant, newTier);
        
        return tenantRepository.save(tenant);
    }
    
    /**
     * Check if tenant can perform verification
     */
    public boolean canPerformVerification(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> tenant.isInGoodStanding() && !tenant.hasExceededVerificationLimit())
                .orElse(false);
    }
    
    /**
     * Check if tenant can register product
     */
    public boolean canRegisterProduct(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> tenant.isInGoodStanding() && 
                              !tenant.hasExceededRegistrationLimit() && 
                              tenant.canAddProducts())
                .orElse(false);
    }
    
    /**
     * Increment tenant verification counter
     */
    @Transactional
    public void incrementVerifications(UUID tenantId) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.incrementVerifications();
            tenantRepository.save(tenant);
        });
    }
    
    /**
     * Increment tenant registration counter
     */
    @Transactional
    public void incrementRegistrations(UUID tenantId) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.incrementRegistrations();
            tenant.setTotalProducts(tenant.getTotalProducts() + 1);
            tenantRepository.save(tenant);
        });
    }
    
    /**
     * Get tenant by ID
     */
    public Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }
    
    /**
     * Get tenant by slug
     */
    public Tenant getTenantBySlug(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }
    
    /**
     * Deactivate tenant
     */
    @Transactional
    public void deactivateTenant(UUID tenantId) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.setActive(false);
            tenantRepository.save(tenant);
            log.info("Deactivated tenant: {}", tenantId);
        });
    }
}
