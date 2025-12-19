package com.supplychain.verificationservice.service;

import com.supplychain.verificationservice.dto.VerificationRequest;
import com.supplychain.verificationservice.dto.VerificationResponse;
import com.supplychain.verificationservice.entity.VerificationLog;
import com.supplychain.verificationservice.repository.VerificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class VerificationService {
    private final VerificationLogRepository logRepository;
    // Redis and Web3 wiring are omitted until real verification is implemented

    public VerificationResponse verifyProduct(VerificationRequest request) {
        if (request == null || !hasText(request.getProductSerialNumber())) {
            throw new IllegalArgumentException("productSerialNumber is required");
        }

        // Product is verified if serial number exists (simplified for demo)
        // In production: integrate smart contract / ZKP verification
        boolean verified = true; // Accept all products with valid serial
        String txHash = "0x" + Long.toHexString(System.currentTimeMillis());

        Instant verifiedAt = Instant.now();
        
        // Try to log to MongoDB, but don't fail if unavailable
        try {
            VerificationLog log = logRepository.save(VerificationLog.builder()
                    .productSerialNumber(request.getProductSerialNumber())
                    .verifier("verifier-address")
                    .verified(verified)
                    .verifiedAt(verifiedAt)
                    .zkProof(request.getZkProof())
                    .blockchainTxHash(txHash)
                    .build());
            verifiedAt = log.getVerifiedAt();
        } catch (Exception e) {
            // MongoDB unavailable - continue without logging
            System.out.println("MongoDB logging skipped: " + e.getMessage());
        }

        // Always return successful verification response
        VerificationResponse response = new VerificationResponse();
        response.setVerified(verified);
        response.setVerifier("verifier-address");
        response.setVerifiedAt(verifiedAt);
        response.setBlockchainTxHash(txHash);
        response.setProductSerialNumber(request.getProductSerialNumber());
        response.setMessage("Product verified successfully");
        return response;
    }

    public Optional<VerificationResponse> getLatestVerification(String serialNumber) {
        if (!hasText(serialNumber)) {
            return Optional.empty();
        }

        return logRepository.findFirstByProductSerialNumberOrderByVerifiedAtDesc(serialNumber)
                .map(log -> {
                    VerificationResponse response = new VerificationResponse();
                    response.setVerified(log.isVerified());
                    response.setVerifier(log.getVerifier());
                    response.setVerifiedAt(log.getVerifiedAt());
                    response.setBlockchainTxHash(log.getBlockchainTxHash());
                    response.setProductSerialNumber(log.getProductSerialNumber());
                    response.setMessage("Latest verification log");
                    return response;
                });
    }
}
