package com.supplychain.common.exception;

/**
 * Exception thrown when rate limit is exceeded
 */
public class RateLimitExceededException extends RuntimeException {
    
    private final int limit;
    private final long retryAfter;
    
    public RateLimitExceededException(int limit, long retryAfter) {
        super(String.format("Rate limit exceeded. Limit: %d per minute. Retry after %d seconds.", 
                           limit, retryAfter));
        this.limit = limit;
        this.retryAfter = retryAfter;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public long getRetryAfter() {
        return retryAfter;
    }
}
