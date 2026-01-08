package com.supplychain.common.security;

import com.supplychain.common.model.Tenant;
import com.supplychain.common.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Service for API key management and validation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyService {
    
    private final TenantRepository tenantRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Value("${jwt.secret:defaultSecretKeyForDevelopmentOnlyChangeInProduction}")
    private String jwtSecret;
    
    private static final String API_KEY_PREFIX = "sk_live_";
    private static final int API_KEY_LENGTH = 32; // 32 bytes = 256 bits
    
    /**
     * Generate new API key for tenant
     * Format: sk_live_<32_random_bytes_base64>
     */
    public String generateApiKey() {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return API_KEY_PREFIX + randomPart;
    }
    
    /**
     * Hash API key for storage (SHA-256)
     */
    public String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Get tenant ID from API key
     * Uses caching to minimize database lookups
     */
    @Cacheable(value = "apiKeyCache", key = "#apiKey")
    public UUID getTenantIdFromApiKey(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(API_KEY_PREFIX)) {
            log.warn("Invalid API key format");
            return null;
        }
        
        String hash = hashApiKey(apiKey);
        return tenantRepository.findByApiKeyHash(hash)
                .filter(Tenant::isInGoodStanding)
                .map(Tenant::getId)
                .orElse(null);
    }
    
    /**
     * Get tenant ID from JWT token
     */
    public UUID getTenantIdFromJwt(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            String tenantIdStr = claims.get("tenantId", String.class);
            return tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
            
        } catch (Exception e) {
            log.warn("Failed to parse JWT token", e);
            return null;
        }
    }
    
    /**
     * Validate tenant is active and in good standing
     */
    public boolean validateTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(Tenant::isInGoodStanding)
                .isPresent();
    }
    
    /**
     * Rotate API key for tenant (invalidate old, generate new)
     */
    public String rotateApiKey(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        
        String newApiKey = generateApiKey();
        String newHash = hashApiKey(newApiKey);
        String newPrefix = newApiKey.substring(0, Math.min(12, newApiKey.length()));
        
        tenant.setApiKeyHash(newHash);
        tenant.setApiKeyPrefix(newPrefix);
        tenantRepository.save(tenant);
        
        log.info("API key rotated for tenant: {}", tenantId);
        return newApiKey;
    }
    
    /**
     * Validate API key and return tenant (for initial setup)
     */
    public Tenant validateAndGetTenant(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(API_KEY_PREFIX)) {
            return null;
        }
        
        String hash = hashApiKey(apiKey);
        return tenantRepository.findByApiKeyHash(hash)
                .filter(Tenant::isInGoodStanding)
                .orElse(null);
    }
}
