package com.supplychain.productservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * High-performance cache configuration using Caffeine.
 * Enables sub-millisecond lookups for verified products.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(1000)
                .maximumSize(50000)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .recordStats());
        cacheManager.setCacheNames(List.of(
                "verifications",
                "fastVerifications", 
                "products"
        ));
        return cacheManager;
    }
}
