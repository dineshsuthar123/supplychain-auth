package com.supplychain.productservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** Adds a correlation ID to every application log line and response. */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String trace = request.getHeader("X-Trace-Id"); if (trace == null || trace.isBlank()) trace = UUID.randomUUID().toString();
        MDC.put("trace_id", trace); response.setHeader("X-Trace-Id", trace);
        try { chain.doFilter(request, response); } finally { MDC.clear(); }
    }
}
