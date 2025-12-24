package com.supplychain.productservice.godmode;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * 🔥 TIER-4 GODLY: Pre-Allocated Response Buffer Pool
 * 
 * Eliminates GC pressure from response generation.
 * Uses ring buffer pattern for O(1) allocation.
 * 
 * Target: Zero allocation in hot path
 */
public class ZeroAllocResponsePool {
    
    // Pool of pre-allocated ByteBuffers
    private final ArrayBlockingQueue<ByteBuffer> pool;
    
    // Buffer size (enough for any response)
    private static final int BUFFER_SIZE = 4096;
    
    // Pool size (one per expected concurrent request)
    private static final int POOL_SIZE = 10_000;
    
    // Singleton
    private static volatile ZeroAllocResponsePool INSTANCE;
    
    private ZeroAllocResponsePool() {
        pool = new ArrayBlockingQueue<>(POOL_SIZE);
        
        // Pre-allocate all buffers upfront
        for (int i = 0; i < POOL_SIZE; i++) {
            pool.offer(ByteBuffer.allocateDirect(BUFFER_SIZE));
        }
    }
    
    public static ZeroAllocResponsePool getInstance() {
        if (INSTANCE == null) {
            synchronized (ZeroAllocResponsePool.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ZeroAllocResponsePool();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Borrow a buffer from the pool.
     * Non-blocking: returns null if pool exhausted.
     */
    public ByteBuffer borrow() {
        ByteBuffer buf = pool.poll();
        if (buf != null) {
            buf.clear();
        }
        return buf;
    }
    
    /**
     * Return buffer to pool.
     */
    public void release(ByteBuffer buf) {
        if (buf != null) {
            buf.clear();
            pool.offer(buf);
        }
    }
    
    /**
     * Get pool utilization (for monitoring).
     */
    public int available() {
        return pool.size();
    }
    
    public int capacity() {
        return POOL_SIZE;
    }
}
