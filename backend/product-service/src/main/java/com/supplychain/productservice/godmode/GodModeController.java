package com.supplychain.productservice.godmode;

import jakarta.annotation.PostConstruct;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * 🔥 TIER-4 GODLY CONTROLLER 🔥
 * 
 * Designed for maximum throughput with minimum latency.
 * 
 * Techniques used:
 * - Lock-free in-memory storage
 * - Pre-computed responses (zero serialization)
 * - Zero object allocation in hot path
 * - Direct byte array responses
 * - Thread-local random for fast ID generation
 * 
 * Target Metrics:
 * - RPS: 50,000 - 100,000+
 * - p95 latency: ≤ 20ms
 * - p99 latency: ≤ 30ms
 * - p99.9 latency: ≤ 60ms
 * - Success rate: 99.999%
 */
@RestController
@RequestMapping("/api/godmode")
public class GodModeController {
    
    // Lock-free product store
    private LockFreeProductStore store;
    
    // Request counters (lock-free)
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder verifyCount = new LongAdder();
    private final LongAdder registerCount = new LongAdder();
    
    // Pre-computed responses (zero allocation)
    private static final byte[] OK_BYTES = "{\"s\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VERIFIED_TRUE = "{\"v\":true,\"s\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VERIFIED_FALSE = "{\"v\":false,\"s\":\"nf\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REGISTERED = "{\"r\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EXISTS = "{\"r\":\"ex\"}".getBytes(StandardCharsets.UTF_8);
    
    // Pre-computed hex chars for fast ID generation
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    
    // MediaType constant (avoid allocation)
    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    
    @PostConstruct
    public void init() {
        store = LockFreeProductStore.getInstance();
    }
    
    // ==================== GODLY ENDPOINTS ====================
    
    /**
     * 🚀 ULTRA-FAST: Health check (baseline latency measurement)
     * Should complete in < 1ms
     */
    @GetMapping("/ping")
    public ResponseEntity<byte[]> ping() {
        requestCount.increment();
        return ResponseEntity.ok()
            .contentType(JSON)
            .body(OK_BYTES);
    }
    
    /**
     * 🚀 GODLY: Zero-allocation verification
     * Returns pre-computed byte array - no JSON serialization
     * Target: p99 < 10ms
     */
    @GetMapping("/v/{serial}")
    public ResponseEntity<byte[]> verifyGodMode(@PathVariable String serial) {
        requestCount.increment();
        verifyCount.increment();
        
        // Direct byte response - zero allocation
        byte[] response = store.exists(serial) ? VERIFIED_TRUE : VERIFIED_FALSE;
        
        return ResponseEntity.ok()
            .contentType(JSON)
            .body(response);
    }
    
    /**
     * 🚀 GODLY: Ultra-minimal verification
     * Returns single byte - absolute minimum response
     * Target: p99 < 5ms
     */
    @GetMapping("/x/{serial}")
    public ResponseEntity<byte[]> verifyUltraMinimal(@PathVariable String serial) {
        requestCount.increment();
        verifyCount.increment();
        
        // Single byte response: '1' = verified, '0' = not found
        byte[] response = store.exists(serial) ? new byte[]{'1'} : new byte[]{'0'};
        
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(response);
    }
    
    /**
     * 🚀 GODLY: Fast registration (write path)
     * Pre-computed responses for success/exists
     */
    @PostMapping("/r/{serial}")
    public ResponseEntity<byte[]> registerGodMode(
            @PathVariable String serial,
            @RequestParam(defaultValue = "Product") String name,
            @RequestParam(defaultValue = "Manufacturer") String mfg) {
        
        requestCount.increment();
        registerCount.increment();
        
        boolean success = store.register(serial, name, mfg);
        
        return ResponseEntity.ok()
            .contentType(JSON)
            .body(success ? REGISTERED : EXISTS);
    }
    
    /**
     * 🚀 GODLY: Register with minimal body parsing
     */
    @PostMapping("/r")
    public ResponseEntity<byte[]> registerWithSerial(@RequestBody String serial) {
        requestCount.increment();
        registerCount.increment();
        
        // Strip quotes if JSON string
        String cleanSerial = serial.replace("\"", "").trim();
        
        boolean success = store.register(cleanSerial, "P-" + cleanSerial, "AutoMfg");
        
        return ResponseEntity.ok()
            .contentType(JSON)
            .body(success ? REGISTERED : EXISTS);
    }
    
    /**
     * 🚀 GODLY: Batch verify (up to 1000 items)
     * Efficient for load testing
     */
    @PostMapping("/batch/v")
    public ResponseEntity<String> batchVerify(@RequestBody String[] serials) {
        requestCount.increment();
        
        int found = 0;
        int notFound = 0;
        
        for (String serial : serials) {
            if (store.exists(serial)) {
                found++;
            } else {
                notFound++;
            }
        }
        
        verifyCount.add(serials.length);
        
        // Minimal JSON response
        return ResponseEntity.ok()
            .contentType(JSON)
            .body("{\"f\":" + found + ",\"n\":" + notFound + ",\"t\":" + serials.length + "}");
    }
    
    // ==================== METRICS ====================
    
    /**
     * Get real-time metrics (for monitoring)
     */
    @GetMapping("/metrics")
    public ResponseEntity<String> metrics() {
        long reads = store.getReadCount();
        long writes = store.getWriteCount();
        long hits = store.getHitCount();
        long misses = store.getMissCount();
        double hitRate = store.getHitRate();
        int size = store.getSize();
        
        String json = String.format(
            "{\"requests\":%d,\"verifies\":%d,\"registers\":%d," +
            "\"reads\":%d,\"writes\":%d,\"hits\":%d,\"misses\":%d," +
            "\"hitRate\":%.4f,\"storeSize\":%d}",
            requestCount.sum(), verifyCount.sum(), registerCount.sum(),
            reads, writes, hits, misses, hitRate, size
        );
        
        return ResponseEntity.ok()
            .contentType(JSON)
            .body(json);
    }
    
    /**
     * Simple stats endpoint
     */
    @GetMapping("/stats")
    public ResponseEntity<byte[]> stats() {
        return ResponseEntity.ok()
            .contentType(JSON)
            .body(("{\"reqs\":" + requestCount.sum() + 
                   ",\"size\":" + store.getSize() + "}").getBytes());
    }
    
    // ==================== WARMUP ====================
    
    /**
     * Warm up the JIT compiler and caches
     */
    @PostMapping("/warmup")
    public ResponseEntity<String> warmup() {
        long start = System.nanoTime();
        
        // Warm up with 100k operations
        for (int i = 0; i < 100_000; i++) {
            store.exists("TEST-" + String.format("%04d", i % 10000));
        }
        
        long elapsed = System.nanoTime() - start;
        double opsPerSec = 100_000.0 / (elapsed / 1_000_000_000.0);
        
        return ResponseEntity.ok()
            .contentType(JSON)
            .body(String.format("{\"warmedUp\":true,\"ops\":100000,\"opsPerSec\":%.0f,\"elapsedMs\":%.2f}",
                opsPerSec, elapsed / 1_000_000.0));
    }
}
