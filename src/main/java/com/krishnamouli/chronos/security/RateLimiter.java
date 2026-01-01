package com.krishnamouli.chronos.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter for protecting cache from abuse.
 * Implements per-client rate limiting with configurable rates.
 * 
 * Algorithm: Token bucket - allows bursts but enforces average rate.
 */
public class RateLimiter {

    private final long ratePerSecond; // Tokens added per second
    private final long bucketSize; // Max tokens in bucket
    private final Map<String, TokenBucket> clientBuckets;
    private final boolean enabled;

    /**
     * Create rate limiter.
     * 
     * @param ratePerSecond Maximum requests per second per client
     * @param burstSize     Maximum burst size (typically 2x rate)
     * @param enabled       Whether rate limiting is enabled
     */
    public RateLimiter(long ratePerSecond, long burstSize, boolean enabled) {
        if (ratePerSecond <= 0) {
            throw new IllegalArgumentException("Rate must be positive");
        }
        this.ratePerSecond = ratePerSecond;
        this.bucketSize = burstSize > 0 ? burstSize : ratePerSecond * 2; // Default: 2x burst
        this.clientBuckets = new ConcurrentHashMap<>();
        this.enabled = enabled;
    }

    /**
     * Check if request should be allowed.
     * 
     * @param clientId Client identifier (IP, token ID, etc.)
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean allowRequest(String clientId) {
        if (!enabled) {
            return true; // Rate limiting disabled
        }

        TokenBucket bucket = clientBuckets.computeIfAbsent(
                clientId,
                k -> new TokenBucket(bucketSize, ratePerSecond));

        return bucket.tryConsume();
    }

    /**
     * Get current rate for a client (for monitoring).
     * 
     * @param clientId Client identifier
     * @return Current tokens available
     */
    public long getCurrentTokens(String clientId) {
        TokenBucket bucket = clientBuckets.get(clientId);
        return bucket != null ? bucket.getAvailableTokens() : bucketSize;
    }

    /**
     * Reset rate limit for a client (admin operation).
     */
    public void resetClient(String clientId) {
        clientBuckets.remove(clientId);
    }

    /**
     * Token bucket implementation.
     * Thread-safe via atomic operations.
     */
    private static class TokenBucket {
        private final long capacity;
        private final long refillRate; // tokens per second
        private final AtomicLong tokens;
        private volatile long lastRefillTime;

        TokenBucket(long capacity, long refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = new AtomicLong(capacity); // Start full
            this.lastRefillTime = System.nanoTime();
        }

        boolean tryConsume() {
            refill();

            // Try to consume a token atomically
            while (true) {
                long current = tokens.get();
                if (current <= 0) {
                    return false; // No tokens available
                }
                if (tokens.compareAndSet(current, current - 1)) {
                    return true; // Successfully consumed
                }
                // CAS failed, retry
            }
        }

        private void refill() {
            long now = System.nanoTime();
            long timePassed = now - lastRefillTime;

            // Calculate tokens to add based on time passed
            long nanosPerSecond = 1_000_000_000L;
            long tokensToAdd = (timePassed * refillRate) / nanosPerSecond;

            if (tokensToAdd > 0) {
                lastRefillTime = now;

                // Add tokens up to capacity
                while (true) {
                    long current = tokens.get();
                    long newValue = Math.min(capacity, current + tokensToAdd);
                    if (tokens.compareAndSet(current, newValue)) {
                        break;
                    }
                }
            }
        }

        long getAvailableTokens() {
            refill();
            return tokens.get();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getRatePerSecond() {
        return ratePerSecond;
    }
}
