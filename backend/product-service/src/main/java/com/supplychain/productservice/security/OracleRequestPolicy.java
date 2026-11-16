package com.supplychain.productservice.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Defense in depth when the private API is reached without the public proxy. */
@Component
@Profile("oracle")
@Order(-200)
public class OracleRequestPolicy extends OncePerRequestFilter {
    private final java.util.concurrent.Semaphore imageSlots = new java.util.concurrent.Semaphore(2);
    @Value("${supplyprint.cors.allowed-origins}") private String origins;
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean allowed = path.matches("/auth/(register|login|refresh|logout|me|health)")
                || path.matches("/api/(dashboard|enroll/image|verify/image|verify/[^/]+/log)")
                || path.matches("/actuator/health(/readiness|/liveness)?") || path.equals("/error");
        if (!allowed) { response.sendError(404); return; }
        if (path.startsWith("/auth/") && request.getMethod().equals("POST")) {
            String origin = request.getHeader("Origin");
            if (origin == null || Arrays.stream(origins.split(",")).map(String::trim).noneMatch(origin::equals)) {
                response.sendError(403); return;
            }
        }
        if (path.endsWith("/image")) {
            if (!imageSlots.tryAcquire()) { response.setHeader("Retry-After", "2"); response.sendError(429); return; }
            try { chain.doFilter(request, response); } finally { imageSlots.release(); }
        } else chain.doFilter(request, response);
    }
}
