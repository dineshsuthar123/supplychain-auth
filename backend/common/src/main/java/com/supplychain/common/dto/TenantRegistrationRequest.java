package com.supplychain.common.dto;

import com.supplychain.common.model.Tenant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for tenant registration request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegistrationRequest {
    
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
    private String name;
    
    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    @Size(min = 3, max = 255, message = "Slug must be between 3 and 255 characters")
    private String slug;
    
    @Size(max = 500, message = "Description must be less than 500 characters")
    private String description;
    
    @Size(max = 100, message = "Industry must be less than 100 characters")
    private String industry;
    
    @Size(max = 100, message = "Country must be less than 100 characters")
    private String country;
    
    private Tenant.SubscriptionTier subscriptionTier = Tenant.SubscriptionTier.FREE;
}
