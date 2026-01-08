package com.supplychain.common.ratelimit;

import com.supplychain.common.context.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting filter using Token Bucket algorithm with Redis
 * Prevents API abuse and ensures fair usage across tenants
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Rate limits per subscription tier (requests per minute)
    private static final int FREE_TIER_LIMIT = 60;           // 1 req/sec
    private static final int STARTER_TIER_LIMIT = 300;       // 5 req/sec
    private static final int PROFESSIONAL_TIER_LIMIT = 1000; // 16 req/sec
    private static final int ENTERPRISE_TIER_LIMIT = 10000;  // 166 req/sec
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Skip rate limiting for health checks and public endpoints
        if (isPublicEndpoint(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            UUID tenantId = TenantContext.getCurrentTenantOrNull();
            if (tenantId == null) {
                // No tenant context, allow (will be caught by authentication)
                chain.doFilter(request, response);
                return;
            }
            
            // Check rate limit
            if (!checkRateLimit(tenantId)) {
                sendRateLimitExceeded(httpResponse, tenantId);
                return;
            }
            
            // Add rate limit headers
            addRateLimitHeaders(httpResponse, tenantId);
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error in rate limit filter", e);
            // Don't block request on rate limiting errors
            chain.doFilter(request, response);
        }
    }
    
    /**
     * Check if tenant has exceeded rate limit using Redis
     * Uses sliding window counter algorithm
     */
    private boolean checkRateLimit(UUID tenantId) {
        String key = "ratelimit:" + tenantId.toString();
        Long currentMinute = System.currentTimeMillis() / 60000;
        String minuteKey = key + ":" + currentMinute;
        
        try {
            // Increment counter for current minute
            Long count = redisTemplate.opsForValue().increment(minuteKey);
            
            // Set expiry on first access (2 minutes to handle clock skew)
            if (count == 1) {
                redisTemplate.expire(minuteKey, 2, TimeUnit.MINUTES);
            }
            
            // Get tenant's rate limit based on subscription tier
            int limit = getTenantRateLimit(tenantId);
            
            if (count > limit) {
                log.warn("Rate limit exceeded for tenant {}: {} requests in current minute", 
                        tenantId, count);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Redis error during rate limiting, allowing request", e);
            return true; // Fail open on Redis errors
        }
    }
    
    /**
     * Get rate limit for tenant based on subscription tier
     */
    private int getTenantRateLimit(UUID tenantId) {
        // TODO: Query tenant's subscription tier from database/cache
        // For now, return professional tier limit
        return PROFESSIONAL_TIER_LIMIT;
    }
    
    /**
     * Add rate limit information to response headers
     */
    private void addRateLimitHeaders(HttpServletResponse response, UUID tenantId) {
        int limit = getTenantRateLimit(tenantId);
        String key = "ratelimit:" + tenantId.toString();
        Long currentMinute = System.currentTimeMillis() / 60000;
        String minuteKey = key + ":" + currentMinute;
        
        try {
            Long currentCount = redisTemplate.opsForValue().get(minuteKey) != null 
                ? Long.parseLong(redisTemplate.opsForValue().get(minuteKey))
                : 0L;
            
            int remaining = Math.max(0, limit - currentCount.intValue());
            long resetTime = (currentMinute + 1) * 60; // Next minute epoch
            
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
            
        } catch (Exception e) {
            log.warn("Failed to add rate limit headers", e);
        }
    }
    
    /**
     * Send 429 Too Many Requests response
     */
    private void sendRateLimitExceeded(HttpServletResponse response, UUID tenantId) throws IOException {
        int limit = getTenantRateLimit(tenantId);
        long retryAfter = 60; // 1 minute
        
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setContentType("application/json");
        
        String jsonResponse = String.format(
            "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Limit: %d per minute. Please try again in %d seconds.\",\"retryAfter\":%d}",
            limit, retryAfter, retryAfter
        );
        
        response.getWriter().write(jsonResponse);
    }
    
    /**
     * Check if endpoint is public (no rate limiting)
     */
    private boolean isPublicEndpoint(String uri) {
        String[] publicPaths = {
            "/actuator/health",
            "/actuator/prometheus",
            "/swagger-ui",
            "/v3/api-docs",
            "/favicon.ico"
        };
        
        for (String path : publicPaths) {
            if (uri.startsWith(path)) {
                return true;
            }
        }
        
        return false;
    }
}
