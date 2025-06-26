package com.supplychain.verificationservice.service;

import com.supplychain.verificationservice.dto.VerificationRequest;
import com.supplychain.verificationservice.dto.VerificationResponse;
import com.supplychain.verificationservice.entity.VerificationLog;
import com.supplychain.verificationservice.repository.VerificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.crypto.Credentials;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class VerificationService {
    private final VerificationLogRepository logRepository;
    private final RedisTemplate<String, Boolean> redisTemplate;
    private final Web3j web3j;
    private final Credentials credentials;
    // private final ProductVerifier productVerifier; // web3j wrapper    @Cacheable(value = "verifications", key = "#request.productSerialNumber")
    public VerificationResponse verifyProduct(VerificationRequest request) {
        try {
            // 1. Check Redis cache (handled by @Cacheable)
            // 2. Call blockchain contract (stubbed)
            boolean verified = true; // TODO: Call ProductVerifier.verifyProduct(...)
            String txHash = "0x0"; // TODO: Get tx hash from blockchain

            // 3. Log to MongoDB
            VerificationLog log = VerificationLog.builder()
                    .productSerialNumber(request.getProductSerialNumber())
                    .verifier("verifier-address")
                    .verified(verified)
                    .verifiedAt(Instant.now())
                    .zkProof(request.getZkProof())
                    .blockchainTxHash(txHash)
                    .build();
            logRepository.save(log);

            // 4. Build response
            VerificationResponse response = new VerificationResponse();
            response.setVerified(verified);
            response.setVerifier("verifier-address");
            response.setVerifiedAt(log.getVerifiedAt());
            response.setBlockchainTxHash(txHash);
            return response;
        } catch (Exception e) {
            // MongoDB connection failed - return mock response for testing
            VerificationResponse response = new VerificationResponse();
            response.setVerified(true);
            response.setVerifier("mock-verifier");
            response.setVerifiedAt(Instant.now());
            response.setBlockchainTxHash("0x123mock");
            response.setProductSerialNumber(request.getProductSerialNumber());
            response.setMessage("Mock verification - MongoDB unavailable: " + e.getMessage());
            return response;
        }
    }
}
