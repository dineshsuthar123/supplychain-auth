package com.supplychain.productservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 🔥 TIER-4 GODLY Cache Configuration
 * 
 * Uses Caffeine for sub-microsecond lookups.
 * Pre-sized for zero rehashing.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Default cache manager for standard profiles
     */
    @Bean
    @Profile("!godmode")
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(10000)
                .maximumSize(100000)
                .expireAfterWrite(300, TimeUnit.SECONDS)
                .recordStats());
        cacheManager.setCacheNames(List.of(
                "verifications",
                "fastVerifications", 
                "products"
        ));
        return cacheManager;
    }
    
    /**
     * 🔥 GODLY cache manager - maximum performance
     */
    @Bean
    @Profile("godmode")
    public CacheManager godModeCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // Pre-allocate for zero rehashing
                .initialCapacity(100000)
                // Massive cache size
                .maximumSize(1_000_000)
                // Long TTL - minimize evictions
                .expireAfterWrite(600, TimeUnit.SECONDS)
                // Stats for monitoring
                .recordStats());
        cacheManager.setCacheNames(List.of(
                "verifications",
                "fastVerifications", 
                "products",
                "godmode"
        ));
        return cacheManager;
    }
}
