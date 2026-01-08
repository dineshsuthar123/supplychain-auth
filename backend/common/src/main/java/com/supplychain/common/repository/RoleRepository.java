package com.supplychain.common.repository;

import com.supplychain.common.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    
    List<Role> findByTenantId(UUID tenantId);
    
    Optional<Role> findByTenantIdAndName(UUID tenantId, String name);
    
    Optional<Role> findByTenantIdAndRoleType(UUID tenantId, Role.RoleType roleType);
    
    List<Role> findByTenantIdAndSystemRole(UUID tenantId, boolean systemRole);
    
    List<Role> findByTenantIdAndActive(UUID tenantId, boolean active);
    
    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
