package com.lotline.identity;

import com.lotline.shared.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      IdentityService identity,
      Json json,
      @Value("${lotline.trusted-origins}") String origins)
      throws Exception {
    var trusted =
        Arrays.stream(origins.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    if (trusted.stream().anyMatch(x -> x.contains("*")))
      throw new IllegalStateException("Trusted origins must be exact URLs");
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(trusted);
    cors.setAllowCredentials(true);
    cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    cors.setAllowedHeaders(
        List.of("Content-Type", "Authorization", "Idempotency-Key", "X-Trace-Id"));
    cors.setExposedHeaders(List.of("X-Trace-Id", "Content-Disposition"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return http.csrf(c -> c.disable())
        .cors(c -> c.configurationSource(source))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health/**",
                        "/api/v1/health",
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/accept-invitation")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (r, s, x) -> {
                      s.setStatus(401);
                      s.setContentType("application/json");
                      s.getWriter()
                          .write(
                              json.write(
                                  Map.of(
                                      "code",
                                      "SESSION_REQUIRED",
                                      "message",
                                      "Sign in to continue.")));
                    }))
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest r, HttpServletResponse s, FilterChain chain)
                  throws ServletException, IOException {
                String trace = r.getHeader("X-Trace-Id");
                if (trace == null || !trace.matches("[A-Za-z0-9_-]{1,80}"))
                  trace = UUID.randomUUID().toString();
                MDC.put("trace_id", trace);
                s.setHeader("X-Trace-Id", trace);
                s.setHeader("Cache-Control", "private, no-store");
                s.setHeader("CDN-Cache-Control", "no-store");
                s.setHeader("Vercel-CDN-Cache-Control", "no-store");
                s.setHeader("X-Content-Type-Options", "nosniff");
                try {
                  if (r.getRequestURI().startsWith("/api/")
                      && !"GET".equals(r.getMethod())
                      && !"OPTIONS".equals(r.getMethod())) {
                    String origin = r.getHeader("Origin");
                    if ((r.getRequestURI().startsWith("/api/v1/auth/") && origin == null)
                        || (origin != null && !trusted.contains(origin)))
                      throw new DomainError(
                          403,
                          "ORIGIN_DENIED",
                          "This browser origin is not trusted for authentication or commands.");
                  }
                  String bearer = r.getHeader("Authorization");
                  if (bearer != null && bearer.startsWith("Bearer ")) {
                    Member m = identity.authenticate(bearer.substring(7));
                    if (r.getHeader("X-Workspace-Id") != null
                        && !m.workspaceId().toString().equals(r.getHeader("X-Workspace-Id")))
                      throw DomainError.forbidden();
                    SecurityContextHolder.getContext()
                        .setAuthentication(
                            new UsernamePasswordAuthenticationToken(m, null, List.of()));
                  }
                  chain.doFilter(r, s);
                } catch (DomainError e) {
                  s.setStatus(e.status);
                  s.setContentType("application/json");
                  s.getWriter()
                      .write(
                          json.write(
                              Map.of("code", e.code, "message", e.getMessage(), "traceId", trace)));
                } finally {
                  SecurityContextHolder.clearContext();
                  MDC.clear();
                }
              }
            },
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
