package com.supplychain.productservice.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Lightweight fixed-window guard; use a distributed limiter for multi-instance deployment. */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final Cache<String, AtomicInteger> requests = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();
    @Value("${supplyprint.rate-limit.requests-per-minute:120}") private int limit;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/"); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String key = request.getRemoteAddr() + ':' + request.getRequestURI();
        int used = requests.get(key, unused -> new AtomicInteger()).incrementAndGet();
        if (used > limit) { response.setStatus(429); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"Rate limit exceeded\"}"); return; }
        chain.doFilter(request, response);
    }
}
