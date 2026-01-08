package com.supplychain.common.exception;

import java.util.UUID;

/**
 * Exception thrown when tenant is not found
 */
public class TenantNotFoundException extends RuntimeException {
    
    private final UUID tenantId;
    
    public TenantNotFoundException(UUID tenantId) {
        super("Tenant not found: " + tenantId);
        this.tenantId = tenantId;
    }
    
    public TenantNotFoundException(String slug) {
        super("Tenant not found: " + slug);
        this.tenantId = null;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
}
