package com.supplychain.verificationservice.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class VerificationResponse {
    private boolean verified;
    private String verifier;
    private Instant verifiedAt;
    private String blockchainTxHash;
    private String productSerialNumber;
    private String message;
}
