package com.supplychain.common.context;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Thread-local storage for current tenant context
 * Used for multi-tenant data isolation
 */
@Slf4j
public class TenantContext {
    
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> adminAccess = new ThreadLocal<>();
    
    private TenantContext() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Set current tenant ID for this thread
     * Should be called by authentication filter/interceptor
     */
    public static void setCurrentTenant(UUID tenantId) {
        if (tenantId == null) {
            log.warn("Attempting to set null tenant ID");
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        currentTenant.set(tenantId);
        log.debug("Set current tenant: {}", tenantId);
    }
    
    /**
     * Get current tenant ID
     * @throws IllegalStateException if no tenant is set
     */
    public static UUID getCurrentTenant() {
        UUID tenantId = currentTenant.get();
        if (tenantId == null) {
            log.error("No tenant context available in current thread");
            throw new IllegalStateException("No tenant context available. Request may be missing authentication.");
        }
        return tenantId;
    }
    
    /**
     * Get current tenant ID without throwing exception
     * @return tenant ID or null if not set
     */
    public static UUID getCurrentTenantOrNull() {
        return currentTenant.get();
    }
    
    /**
     * Check if tenant context is set
     */
    public static boolean hasTenantContext() {
        return currentTenant.get() != null;
    }
    
    /**
     * Set current user identifier
     */
    public static void setCurrentUser(String userId) {
        currentUser.set(userId);
        log.debug("Set current user: {}", userId);
    }
    
    /**
     * Get current user identifier
     */
    public static String getCurrentUser() {
        return currentUser.get();
    }
    
    /**
     * Set admin access flag (for super admin operations)
     */
    public static void setAdminAccess(boolean isAdmin) {
        adminAccess.set(isAdmin);
        log.debug("Set admin access: {}", isAdmin);
    }
    
    /**
     * Check if current request has admin access
     */
    public static boolean hasAdminAccess() {
        Boolean admin = adminAccess.get();
        return admin != null && admin;
    }
    
    /**
     * Clear all context (MUST be called after request completion)
     * Should be in finally block of filter/interceptor
     */
    public static void clear() {
        UUID tenantId = currentTenant.get();
        String userId = currentUser.get();
        
        currentTenant.remove();
        currentUser.remove();
        adminAccess.remove();
        
        log.debug("Cleared context for tenant: {}, user: {}", tenantId, userId);
    }
    
    /**
     * Execute code with temporary tenant context
     * Automatically clears context after execution
     */
    public static <T> T executeWithTenant(UUID tenantId, TenantContextCallback<T> callback) {
        UUID previousTenant = currentTenant.get();
        try {
            setCurrentTenant(tenantId);
            return callback.execute();
        } finally {
            if (previousTenant != null) {
                currentTenant.set(previousTenant);
            } else {
                currentTenant.remove();
            }
        }
    }
    
    /**
     * Execute code with admin privileges
     */
    public static <T> T executeAsAdmin(AdminContextCallback<T> callback) {
        Boolean previousAdmin = adminAccess.get();
        try {
            setAdminAccess(true);
            return callback.execute();
        } finally {
            if (previousAdmin != null) {
                adminAccess.set(previousAdmin);
            } else {
                adminAccess.remove();
            }
        }
    }
    
    @FunctionalInterface
    public interface TenantContextCallback<T> {
        T execute();
    }
    
    @FunctionalInterface
    public interface AdminContextCallback<T> {
        T execute();
    }
}
