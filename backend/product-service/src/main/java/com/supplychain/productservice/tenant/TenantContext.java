package com.supplychain.productservice.tenant;

import java.util.UUID;

/** Request-scoped tenant identity populated exclusively from a validated JWT. */
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private TenantContext() { }
    public static void set(UUID tenantId) { CURRENT.set(tenantId); }
    public static UUID getRequired() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) throw new IllegalStateException("Tenant context is required");
        return tenantId;
    }
    public static void clear() { CURRENT.remove(); }
}
