package com.supplychain.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProductRegistrationRequest {
    @NotBlank
    private String serialNumber;
    @NotBlank
    private String name;
    @NotBlank
    private String manufacturer;
    @NotBlank
    private String metadataUri;
}
