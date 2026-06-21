package com.supplychain.productservice.repository;
import com.supplychain.productservice.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TenantRepository extends JpaRepository<Tenant, UUID> { }
