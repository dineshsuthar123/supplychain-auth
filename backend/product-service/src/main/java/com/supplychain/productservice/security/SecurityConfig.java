package com.supplychain.productservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final ApiRateLimitFilter rateLimitFilter;
    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ApiRateLimitFilter rateLimitFilter) { this.jwtFilter = jwtFilter; this.rateLimitFilter = rateLimitFilter; }
    @Value("${supplyprint.cors.allowed-origins:http://localhost:3000}") private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh", "/auth/health", "/actuator/health", "/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/enroll", "/api/verify").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/enroll/**").hasAnyRole("MANUFACTURER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/verify/**").hasAnyRole("VERIFIER", "MANUFACTURER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/verify/**", "/api/dashboard").hasAnyRole("AUDITOR", "VERIFIER", "MANUFACTURER", "ADMIN")
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable());
        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins); config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept")); config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true); config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;
    }
}
