package com.supplychain.verificationservice.dto;

import lombok.Data;

@Data
public class VerificationRequest {
    private String productSerialNumber;
    private String zkProof; // For ZKP-based verification
}
