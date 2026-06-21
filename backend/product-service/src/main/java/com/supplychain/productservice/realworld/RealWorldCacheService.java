package com.supplychain.productservice.realworld;

import com.supplychain.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Cache-Aside Verification Service (in-memory - Redis removed). */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealWorldCacheService {
    private final ProductRepository productRepository;
    private final ConcurrentHashMap<String, Instant> hitCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> missCache = new ConcurrentHashMap<>();
    private static final Duration HIT_TTL = Duration.ofMinutes(10);
    private static final Duration MISS_TTL = Duration.ofSeconds(30);

    public VerificationResult verify(String serial) {
        long start = System.nanoTime();
        Instant he = hitCache.get(serial);
        if (he != null && Instant.now().isBefore(he)) {
            publishAuditAsync(serial, true, "CACHE_HIT");
            return VerificationResult.hit(serial, System.nanoTime() - start);
        }
        Instant me = missCache.get(serial);
        if (me != null && Instant.now().isBefore(me)) {
            publishAuditAsync(serial, false, "CACHE_MISS");
            return VerificationResult.miss(serial, System.nanoTime() - start);
        }
        boolean found = productRepository.existsBySerialNumber(serial);
        long ns = System.nanoTime() - start;
        if (found) {
            hitCache.put(serial, Instant.now().plus(HIT_TTL));
            missCache.remove(serial);
            publishAuditAsync(serial, true, "DB_HIT");
            return VerificationResult.hit(serial, ns);
        } else {
            missCache.put(serial, Instant.now().plus(MISS_TTL));
            publishAuditAsync(serial, false, "DB_MISS");
            return VerificationResult.miss(serial, ns);
        }
    }

    public void onProductRegistered(String serial) {
        hitCache.put(serial, Instant.now().plus(HIT_TTL));
        missCache.remove(serial);
    }

    public void warmUpCache(Iterable<String> serials) {
        Instant exp = Instant.now().plus(HIT_TTL);
        serials.forEach(s -> hitCache.put(s, exp));
    }

    @Async
    public void publishAuditAsync(String serial, boolean verified, String source) {
        log.debug("audit serial={} verified={} source={}", serial, verified, source);
    }

    public record VerificationResult(String serial, boolean verified, long latencyNs) {
        public static VerificationResult hit(String s, long ns) { return new VerificationResult(s, true, ns); }
        public static VerificationResult miss(String s, long ns) { return new VerificationResult(s, false, ns); }
        public double latencyMs() { return latencyNs / 1_000_000.0; }
    }
}
