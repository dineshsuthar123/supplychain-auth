package com.supplychain.productservice.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class ProductResponse {
    private UUID id;
    private String serialNumber;
    private String name;
    private String manufacturer;
    private String metadataUri;
    private Instant registeredAt;
    private String nftTokenId;
}
