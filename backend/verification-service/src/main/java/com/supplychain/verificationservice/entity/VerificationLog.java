package com.supplychain.verificationservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "verifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationLog {
    @Id
    private String id;
    private String productSerialNumber;
    private String verifier;
    private boolean verified;
    private Instant verifiedAt;
    private String zkProof;
    private String blockchainTxHash;
}
