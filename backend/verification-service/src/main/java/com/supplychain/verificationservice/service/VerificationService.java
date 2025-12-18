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

        // TODO: Integrate real verification logic (smart contract / ZKP)
        boolean verified = hasText(request.getZkProof());
        String txHash = verified ? "pending" : null;

        try {
            VerificationLog log = logRepository.save(VerificationLog.builder()
                    .productSerialNumber(request.getProductSerialNumber())
                    .verifier("verifier-address")
                    .verified(verified)
                    .verifiedAt(Instant.now())
                    .zkProof(request.getZkProof())
                    .blockchainTxHash(txHash)
                    .build());

            VerificationResponse response = new VerificationResponse();
            response.setVerified(log.isVerified());
            response.setVerifier(log.getVerifier());
            response.setVerifiedAt(log.getVerifiedAt());
            response.setBlockchainTxHash(log.getBlockchainTxHash());
            response.setProductSerialNumber(log.getProductSerialNumber());
            response.setMessage(verified ? "Verification recorded" : "Verification pending / not proven");
            return response;
        } catch (Exception e) {
            VerificationResponse response = new VerificationResponse();
            response.setVerified(false);
            response.setVerifier("unverified");
            response.setVerifiedAt(Instant.now());
            response.setBlockchainTxHash(null);
            response.setProductSerialNumber(request.getProductSerialNumber());
            response.setMessage("Verification failed: " + e.getMessage());
            return response;
        }
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
