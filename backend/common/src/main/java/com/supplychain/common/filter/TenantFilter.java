package com.supplychain.common.filter;

import com.supplychain.common.context.TenantContext;
import com.supplychain.common.security.ApiKeyService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that extracts tenant information from request
 * and sets up tenant context for the request lifecycle
 */
@Component
@Order(1) // Execute before Spring Security
@Slf4j
@RequiredArgsConstructor
public class TenantFilter implements Filter {
    
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final ApiKeyService apiKeyService;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            UUID tenantId = extractTenantId(httpRequest);
            
            if (tenantId != null) {
                // Validate tenant and set context
                if (apiKeyService.validateTenant(tenantId)) {
                    TenantContext.setCurrentTenant(tenantId);
                    log.debug("Tenant context set for request: {} {}", 
                             httpRequest.getMethod(), httpRequest.getRequestURI());
                } else {
                    sendUnauthorized(httpResponse, "Invalid or inactive tenant");
                    return;
                }
            } else if (requiresTenantContext(httpRequest)) {
                sendUnauthorized(httpResponse, "Missing tenant authentication");
                return;
            }
            
            // Continue filter chain
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error in tenant filter", e);
            sendUnauthorized(httpResponse, "Authentication error");
        } finally {
            // CRITICAL: Always clear context to prevent thread pool pollution
            TenantContext.clear();
        }
    }
    
    /**
     * Extract tenant ID from request headers
     * Priority: 1) API Key, 2) JWT Token, 3) X-Tenant-ID header
     */
    private UUID extractTenantId(HttpServletRequest request) {
        // 1. Try API Key authentication
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKeyService.getTenantIdFromApiKey(apiKey);
        }
        
        // 2. Try JWT token (Authorization header)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            return apiKeyService.getTenantIdFromJwt(token);
        }
        
        // 3. Explicit tenant ID header (for testing/development)
        String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);
        if (tenantIdHeader != null && !tenantIdHeader.isEmpty()) {
            try {
                return UUID.fromString(tenantIdHeader);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenant ID format: {}", tenantIdHeader);
            }
        }
        
        return null;
    }
    
    /**
     * Check if endpoint requires tenant context
     * Public endpoints (like health checks, docs) don't need tenant
     */
    private boolean requiresTenantContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Whitelist of public endpoints
        String[] publicPaths = {
            "/actuator/health",
            "/actuator/prometheus",
            "/swagger-ui",
            "/v3/api-docs",
            "/api/public",
            "/api/auth/register",  // Tenant registration
            "/api/auth/login"      // Initial authentication
        };
        
        for (String publicPath : publicPaths) {
            if (path.startsWith(publicPath)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Send 401 Unauthorized response
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
            "{\"error\":\"Unauthorized\",\"message\":\"%s\"}", message
        ));
    }
}
