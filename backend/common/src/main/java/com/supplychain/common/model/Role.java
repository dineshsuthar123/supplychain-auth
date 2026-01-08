package com.supplychain.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Role entity for RBAC (Role-Based Access Control)
 */
@Entity
@Table(name = "roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "tenant_id"})
}, indexes = {
    @Index(name = "idx_roles_tenant", columnList = "tenant_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, length = 50)
    private String name;
    
    @Column(length = 255)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleType roleType;
    
    @Column(nullable = false)
    private UUID tenantId; // Each tenant has their own roles
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", 
                     joinColumns = @JoinColumn(name = "role_id"),
                     indexes = @Index(name = "idx_role_permissions_role", columnList = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
    
    @Column(nullable = false)
    private Boolean systemRole = false; // Cannot be deleted/modified
    
    @Column(nullable = false)
    private Boolean active = true;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum RoleType {
        TENANT_ADMIN,       // Full control over tenant
        MANUFACTURER,       // Can register products
        WAREHOUSE_MANAGER,  // Can update product status
        QUALITY_AUDITOR,    // Read-only access for auditing
        API_USER,           // Programmatic access via API
        CONSUMER,           // Can only verify products
        SUPER_ADMIN         // Platform-wide admin (not tenant-specific)
    }
    
    public enum Permission {
        // Product permissions
        PRODUCT_CREATE,
        PRODUCT_READ,
        PRODUCT_UPDATE,
        PRODUCT_DELETE,
        PRODUCT_REGISTER_BLOCKCHAIN,
        
        // Verification permissions
        VERIFICATION_READ,
        VERIFICATION_CREATE,
        
        // User management
        USER_CREATE,
        USER_READ,
        USER_UPDATE,
        USER_DELETE,
        USER_ASSIGN_ROLES,
        
        // Analytics
        ANALYTICS_VIEW,
        ANALYTICS_EXPORT,
        
        // Billing
        BILLING_VIEW,
        BILLING_MANAGE,
        
        // Settings
        SETTINGS_VIEW,
        SETTINGS_UPDATE,
        
        // API keys
        API_KEY_CREATE,
        API_KEY_VIEW,
        API_KEY_REVOKE,
        
        // Audit logs
        AUDIT_VIEW,
        AUDIT_EXPORT,
        
        // IoT devices
        IOT_DEVICE_REGISTER,
        IOT_DEVICE_MANAGE,
        IOT_DATA_VIEW,
        
        // ML/AI
        ML_MODEL_TRAIN,
        ML_ALERTS_VIEW,
        
        // Multi-chain
        BLOCKCHAIN_MANAGE,
        
        // System admin
        TENANT_CREATE,
        TENANT_DELETE,
        SYSTEM_CONFIG
    }
    
    /**
     * Check if role has specific permission
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
    
    /**
     * Check if role has any of the specified permissions
     */
    public boolean hasAnyPermission(Permission... perms) {
        for (Permission perm : perms) {
            if (permissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if role has all of the specified permissions
     */
    public boolean hasAllPermissions(Permission... perms) {
        for (Permission perm : perms) {
            if (!permissions.contains(perm)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Factory method for creating default tenant admin role
     */
    public static Role createTenantAdminRole(UUID tenantId) {
        return Role.builder()
            .name("Tenant Admin")
            .description("Full administrative access to tenant resources")
            .roleType(RoleType.TENANT_ADMIN)
            .tenantId(tenantId)
            .systemRole(true)
            .permissions(Set.of(
                Permission.PRODUCT_CREATE, Permission.PRODUCT_READ, Permission.PRODUCT_UPDATE, Permission.PRODUCT_DELETE,
                Permission.PRODUCT_REGISTER_BLOCKCHAIN,
                Permission.VERIFICATION_READ, Permission.VERIFICATION_CREATE,
                Permission.USER_CREATE, Permission.USER_READ, Permission.USER_UPDATE, Permission.USER_DELETE, Permission.USER_ASSIGN_ROLES,
                Permission.ANALYTICS_VIEW, Permission.ANALYTICS_EXPORT,
                Permission.BILLING_VIEW, Permission.BILLING_MANAGE,
                Permission.SETTINGS_VIEW, Permission.SETTINGS_UPDATE,
                Permission.API_KEY_CREATE, Permission.API_KEY_VIEW, Permission.API_KEY_REVOKE,
                Permission.AUDIT_VIEW, Permission.AUDIT_EXPORT
            ))
            .build();
    }
    
    /**
     * Factory method for creating manufacturer role
     */
    public static Role createManufacturerRole(UUID tenantId) {
        return Role.builder()
            .name("Manufacturer")
            .description("Can register and manage products")
            .roleType(RoleType.MANUFACTURER)
            .tenantId(tenantId)
            .systemRole(true)
            .permissions(Set.of(
                Permission.PRODUCT_CREATE, Permission.PRODUCT_READ, Permission.PRODUCT_UPDATE,
                Permission.PRODUCT_REGISTER_BLOCKCHAIN,
                Permission.VERIFICATION_READ,
                Permission.ANALYTICS_VIEW
            ))
            .build();
    }
    
    /**
     * Factory method for creating auditor role
     */
    public static Role createAuditorRole(UUID tenantId) {
        return Role.builder()
            .name("Quality Auditor")
            .description("Read-only access for compliance auditing")
            .roleType(RoleType.QUALITY_AUDITOR)
            .tenantId(tenantId)
            .systemRole(true)
            .permissions(Set.of(
                Permission.PRODUCT_READ,
                Permission.VERIFICATION_READ,
                Permission.AUDIT_VIEW, Permission.AUDIT_EXPORT,
                Permission.ANALYTICS_VIEW, Permission.ANALYTICS_EXPORT
            ))
            .build();
    }
}
