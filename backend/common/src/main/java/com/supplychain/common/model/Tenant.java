package com.supplychain.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tenant entity for multi-tenant architecture
 * Each tenant represents a manufacturer/brand using the platform
 */
@Entity
@Table(name = "tenants", indexes = {
    @Index(name = "idx_tenants_api_key", columnList = "apiKeyHash"),
    @Index(name = "idx_tenants_stripe_customer", columnList = "stripeCustomerId"),
    @Index(name = "idx_tenants_status", columnList = "subscriptionStatus")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true, length = 255)
    private String name;
    
    @Column(nullable = false, unique = true, length = 255)
    private String slug; // URL-friendly identifier
    
    @Column(length = 500)
    private String description;
    
    @Column(length = 100)
    private String industry; // e.g., "Pharmaceuticals", "Electronics", "Luxury Goods"
    
    @Column(length = 100)
    private String country;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;
    
    @Column(unique = true)
    private String stripeCustomerId;
    
    @Column(unique = true)
    private String stripeSubscriptionId;
    
    @Column(nullable = false, unique = true)
    private String apiKeyHash; // SHA-256 hash of API key
    
    @Column(nullable = false)
    private String apiKeyPrefix; // First 8 characters for identification (e.g., "sk_live_")
    
    // Resource limits based on subscription tier
    @Column(nullable = false)
    private Integer monthlyVerificationLimit = 1000;
    
    @Column(nullable = false)
    private Integer monthlyRegistrationLimit = 100;
    
    @Column(nullable = false)
    private Integer maxProducts = 10000;
    
    @Column(nullable = false)
    private Integer maxUsers = 5;
    
    @Column(nullable = false)
    private Boolean analyticsEnabled = false;
    
    @Column(nullable = false)
    private Boolean apiAccessEnabled = true;
    
    @Column(nullable = false)
    private Boolean multiChainEnabled = false;
    
    @Column(nullable = false)
    private Boolean iotIntegrationEnabled = false;
    
    @Column(nullable = false)
    private Boolean mlFraudDetectionEnabled = false;
    
    // Current usage counters (reset monthly)
    @Column(nullable = false)
    private Integer currentMonthVerifications = 0;
    
    @Column(nullable = false)
    private Integer currentMonthRegistrations = 0;
    
    @Column(nullable = false)
    private Integer totalProducts = 0;
    
    @Column(nullable = false)
    private Integer totalUsers = 1; // Includes the tenant admin
    
    @Column(columnDefinition = "jsonb")
    private String settings; // JSON for flexible configuration
    
    @Column(columnDefinition = "jsonb")
    private String blockchainConfig; // Chain preferences, wallet addresses
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column
    private LocalDateTime subscriptionStartDate;
    
    @Column
    private LocalDateTime subscriptionEndDate;
    
    @Column
    private LocalDateTime lastBillingDate;
    
    @Column
    private LocalDateTime nextBillingDate;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    @Column(nullable = false)
    private Boolean deleted = false;
    
    public enum SubscriptionTier {
        FREE,           // 1K verifications/month, 100 products
        STARTER,        // $49/mo: 10K verifications, 1K products
        PROFESSIONAL,   // $199/mo: 100K verifications, 10K products, analytics
        ENTERPRISE      // Custom: Unlimited, multi-chain, IoT, ML
    }
    
    public enum SubscriptionStatus {
        ACTIVE,
        TRIAL,
        PAST_DUE,
        CANCELED,
        SUSPENDED,
        EXPIRED
    }
    
    /**
     * Check if tenant has exceeded verification limit
     */
    public boolean hasExceededVerificationLimit() {
        return currentMonthVerifications >= monthlyVerificationLimit;
    }
    
    /**
     * Check if tenant has exceeded registration limit
     */
    public boolean hasExceededRegistrationLimit() {
        return currentMonthRegistrations >= monthlyRegistrationLimit;
    }
    
    /**
     * Check if tenant can add more products
     */
    public boolean canAddProducts() {
        return totalProducts < maxProducts;
    }
    
    /**
     * Check if tenant can add more users
     */
    public boolean canAddUsers() {
        return totalUsers < maxUsers;
    }
    
    /**
     * Increment verification counter
     */
    public void incrementVerifications() {
        this.currentMonthVerifications++;
    }
    
    /**
     * Increment registration counter
     */
    public void incrementRegistrations() {
        this.currentMonthRegistrations++;
    }
    
    /**
     * Reset monthly counters (called by scheduled job)
     */
    public void resetMonthlyCounters() {
        this.currentMonthVerifications = 0;
        this.currentMonthRegistrations = 0;
    }
    
    /**
     * Check if tenant is in good standing
     */
    public boolean isInGoodStanding() {
        return active && !deleted && 
               (subscriptionStatus == SubscriptionStatus.ACTIVE || subscriptionStatus == SubscriptionStatus.TRIAL);
    }
}
