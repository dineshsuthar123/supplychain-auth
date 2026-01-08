package com.supplychain.common.dto;

import com.supplychain.common.model.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for tenant response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    
    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String industry;
    private String country;
    private Tenant.SubscriptionTier subscriptionTier;
    private Tenant.SubscriptionStatus subscriptionStatus;
    private String apiKeyPrefix; // Show prefix only, not full key
    
    // Resource limits
    private Integer monthlyVerificationLimit;
    private Integer monthlyRegistrationLimit;
    private Integer maxProducts;
    private Integer maxUsers;
    
    // Current usage
    private Integer currentMonthVerifications;
    private Integer currentMonthRegistrations;
    private Integer totalProducts;
    private Integer totalUsers;
    
    // Feature flags
    private Boolean analyticsEnabled;
    private Boolean multiChainEnabled;
    private Boolean iotIntegrationEnabled;
    private Boolean mlFraudDetectionEnabled;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime subscriptionEndDate;
    private LocalDateTime nextBillingDate;
    
    /**
     * Create response from entity
     */
    public static TenantResponse from(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .description(tenant.getDescription())
                .industry(tenant.getIndustry())
                .country(tenant.getCountry())
                .subscriptionTier(tenant.getSubscriptionTier())
                .subscriptionStatus(tenant.getSubscriptionStatus())
                .apiKeyPrefix(tenant.getApiKeyPrefix())
                .monthlyVerificationLimit(tenant.getMonthlyVerificationLimit())
                .monthlyRegistrationLimit(tenant.getMonthlyRegistrationLimit())
                .maxProducts(tenant.getMaxProducts())
                .maxUsers(tenant.getMaxUsers())
                .currentMonthVerifications(tenant.getCurrentMonthVerifications())
                .currentMonthRegistrations(tenant.getCurrentMonthRegistrations())
                .totalProducts(tenant.getTotalProducts())
                .totalUsers(tenant.getTotalUsers())
                .analyticsEnabled(tenant.getAnalyticsEnabled())
                .multiChainEnabled(tenant.getMultiChainEnabled())
                .iotIntegrationEnabled(tenant.getIotIntegrationEnabled())
                .mlFraudDetectionEnabled(tenant.getMlFraudDetectionEnabled())
                .createdAt(tenant.getCreatedAt())
                .subscriptionEndDate(tenant.getSubscriptionEndDate())
                .nextBillingDate(tenant.getNextBillingDate())
                .build();
    }
}
