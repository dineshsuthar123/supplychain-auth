package com.supplychain.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerificationRequest {
    @NotBlank(message = "Product serial number is required")
    private String productSerialNumber;
    
    private String zkProof;
    private String verifierAddress;
    private String location;
}
