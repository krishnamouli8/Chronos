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
        // Memory accounting calculation
        //
        // ASSUMPTIONS (64-bit JVM with CompressedOOPs enabled):
        // - Object header: 16 bytes (12 bytes mark+klass, 4 bytes padding)
        // - Field references (6 × 8 bytes): 48 bytes (compressed to 4 bytes each = 24,
        // but assume 48 for safety)
        // - Primitive fields (3 × long = 24 bytes)
        // - Array header: 16 bytes
        // - AtomicLong object: 24 bytes (object + long value + padding)
        // Total overhead: ~120 bytes
        //
        // WARNING: This breaks on:
        // - 32-bit JVM (different object layout)
        // - -XX:-UseCompressedOops (8-byte references instead of 4-byte)
        // - Exotic JVMs (J9, Azul, etc may have different layouts)
        //
        // For truly accurate sizing, use
        // java.lang.instrument.Instrumentation.getObjectSize()
        // but that requires -javaagent which adds complexity.
        //
        // Reference: https://www.baeldung.com/java-size-of-object
        return 120 + size;
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
