package com.supplychain.productservice.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;

@Component
@Profile("oracle")
public class OracleDeploymentGuard {
    private final Environment env;
    public OracleDeploymentGuard(Environment env) { this.env = env; }
    @org.springframework.context.annotation.Bean
    org.springframework.security.core.userdetails.UserDetailsService jwtOnlyUserDetails() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Use the JWT authentication API"); };
    }
    @PostConstruct
    void validate() {
        if (Arrays.stream(env.getActiveProfiles()).anyMatch(Set.of("dev", "godmode", "realistic", "local")::contains))
            throw new IllegalStateException("Oracle must not enable a development profile");
        requireSecret("jwt.secret", 43);
        requireSecret("spring.datasource.password", 24);
        requireSecret("spring.flyway.password", 24);
        if (!"supplyprint_app".equals(env.getProperty("spring.datasource.username"))
                || !"supplyprint_migration".equals(env.getProperty("spring.flyway.user")))
            throw new IllegalStateException("Oracle requires separate fixed runtime and migration roles");
        if (env.getProperty("blockchain.enabled", Boolean.class, true)
                || env.getProperty("supplyprint.auth.allow-self-service-operator-roles", Boolean.class, true)
                || !env.getProperty("supplyprint.cookies.secure", Boolean.class, false))
            throw new IllegalStateException("Unsafe Oracle authentication/blockchain configuration");
        for (String origin : env.getRequiredProperty("supplyprint.cors.allowed-origins").split(",")) {
            URI uri = URI.create(origin.trim());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty())
                throw new IllegalStateException("Oracle trusted origins must be exact HTTPS origins");
        }
    }
    private void requireSecret(String property, int minimum) {
        String value = env.getRequiredProperty(property);
        if (value.length() < minimum || value.toLowerCase().matches(".*(changeme|replace-|password123).*"))
            throw new IllegalStateException("Set a strong required secret: " + property);
    }
}
