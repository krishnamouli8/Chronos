package com.krishnamouli.chronos.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single cache entry with metadata for intelligent cache
 * operations.
 * Thread-safe for concurrent access tracking.
 */
public class CacheEntry {
    private final byte[] value;
    private final long createdAt;
    private volatile long expiresAt;
    private volatile long lastAccessTime;
    private final AtomicLong accessCount;
    private final long size;
    private volatile long computeCostMs;
    private volatile int valueHash;

    public CacheEntry(byte[] value, long ttlSeconds) {
        this.value = value;
        this.createdAt = System.nanoTime();
        this.lastAccessTime = this.createdAt;
        this.expiresAt = ttlSeconds > 0 ? System.currentTimeMillis() + (ttlSeconds * 1000) : Long.MAX_VALUE;
        this.accessCount = new AtomicLong(0);
        this.size = value.length;
        this.computeCostMs = 0;
        this.valueHash = computeHash(value);
    }

    public byte[] getValue() {
        recordAccess();
        return value;
    }

    public void recordAccess() {
        lastAccessTime = System.nanoTime();
        accessCount.incrementAndGet();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public void setTTL(long ttlSeconds) {
        this.expiresAt = ttlSeconds > 0 ? System.currentTimeMillis() + (ttlSeconds * 1000) : Long.MAX_VALUE;
    }

    public long getTTL() {
        long remaining = expiresAt - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    public long getAccessCount() {
        return accessCount.get();
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long getAge() {
        return System.nanoTime() - createdAt;
    }

    public long getSize() {
        // Accurate memory accounting including object overhead
        // Object header: 16 bytes (12 bytes header + 4 bytes padding)
        // Field references and primitives:
        // - byte[] value reference: 8 bytes
        // - long createdAt: 8 bytes
        // - volatile long expiresAt: 8 bytes
        // - volatile long lastAccessTime: 8 bytes
        // - AtomicLong accessCount (object): ~32 bytes (16 header + 8 long + padding)
        // - long size: 8 bytes
        // - volatile long computeCostMs: 8 bytes
        // - volatile int valueHash: 4 bytes
        // Array header: 16 bytes (12 bytes header + 4 bytes length)
        // Total overhead: ~116 bytes (rounded to 120 for safety)
        return 120 + size; // size already contains value.length
    }

    public void setComputeCost(long costMs) {
        this.computeCostMs = costMs;
    }

    public long getComputeCost() {
        return computeCostMs;
    }

    public double getAccessesPerHour() {
        long ageMs = getAge() / 1_000_000;
        if (ageMs == 0)
            return 0;
        return (double) accessCount.get() / ageMs * 3600_000;
    }

    public int getValueHash() {
        return valueHash;
    }

    private int computeHash(byte[] data) {
        int hash = 1;
        for (byte b : data) {
            hash = 31 * hash + b;
        }
        return hash;
    }
}
