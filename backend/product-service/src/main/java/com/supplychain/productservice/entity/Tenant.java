package com.supplychain.productservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 255) private String name;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    protected Tenant() { }
    public Tenant(String name) { this.name = name; }
    public UUID getId() { return id; }
    public String getName() { return name; }
}
