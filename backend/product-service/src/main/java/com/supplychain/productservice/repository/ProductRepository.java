package com.supplychain.productservice.repository;

import com.supplychain.productservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Optional<Product> findBySerialNumber(String serialNumber);
    
    /**
     * Ultra-fast existence check - returns only boolean, no entity loading.
     * Uses indexed lookup on serial_number column.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Product p WHERE p.serialNumber = :serial")
    boolean existsBySerialNumber(@Param("serial") String serialNumber);
}
