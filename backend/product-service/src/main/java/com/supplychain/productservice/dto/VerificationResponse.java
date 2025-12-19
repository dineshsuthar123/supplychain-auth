package com.supplychain.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {
    private String productSerialNumber;
    private String productName;
    private String manufacturer;
    private boolean verified;
    private Instant verifiedAt;
    private String transactionHash;
    private String message;
    private Long blockNumber;
    private String verificationId;
}
