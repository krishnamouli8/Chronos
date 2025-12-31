package com.krishnamouli.chronos.core;

import com.krishnamouli.chronos.core.eviction.EvictionPolicy;
import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * High-performance segmented cache with lock striping.
 * Provides Redis-like operations with intelligent features.
 */
public class ChronosCache {
    private static final Logger logger = LoggerFactory.getLogger(ChronosCache.class);

    private final CacheSegment[] segments;
    private final int segmentCount;
    private final int segmentMask;
    private final ScheduledExecutorService cleanupExecutor;

    public ChronosCache(int segmentCount, long maxMemoryBytes, EvictionPolicy evictionPolicyTemplate) {
        this.segmentCount = nextPowerOfTwo(segmentCount);
        this.segmentMask = this.segmentCount - 1;
        this.segments = new CacheSegment[this.segmentCount];

        long memoryPerSegment = maxMemoryBytes / this.segmentCount;

        for (int i = 0; i < this.segmentCount; i++) {
            EvictionPolicy policy = createPolicy(evictionPolicyTemplate);
            this.segments[i] = new CacheSegment(policy, memoryPerSegment);
        }

        // Periodic cleanup of expired entries
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chronos-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupExpired, 60, 60, TimeUnit.SECONDS);

        logger.info("Chronos cache initialized: segments={}, memory={}MB",
                this.segmentCount, maxMemoryBytes / (1024 * 1024));
    }

    // Convenience constructor for tests (alternative parameter order)
    public ChronosCache(int segmentCount, EvictionPolicy evictionPolicyTemplate, long maxMemoryBytes) {
        this(segmentCount, maxMemoryBytes, evictionPolicyTemplate);
    }

    public byte[] get(String key) {
        CacheSegment segment = getSegment(key);
        CacheEntry entry = segment.get(key);
        return entry != null ? entry.getValue() : null;
    }

    public CacheEntry getEntry(String key) {
        CacheSegment segment = getSegment(key);
        return segment.get(key);
    }

    public void put(String key, byte[] value, long ttlSeconds) {
        CacheEntry entry = new CacheEntry(value, ttlSeconds);
        CacheSegment segment = getSegment(key);
        segment.put(key, entry);
    }

    public boolean delete(String key) {
        CacheSegment segment = getSegment(key);
        return segment.remove(key);
    }

    public boolean expire(String key, long ttlSeconds) {
        CacheSegment segment = getSegment(key);
        CacheEntry entry = segment.get(key);
        if (entry != null) {
            entry.setTTL(ttlSeconds);
            return true;
        }
        return false;
    }

    public long ttl(String key) {
        CacheSegment segment = getSegment(key);
        CacheEntry entry = segment.get(key);
        return entry != null ? entry.getTTL() : -2; // -2 means key doesn't exist
    }

    public void clear() {
        for (CacheSegment segment : segments) {
            segment.clear();
        }
        logger.info("Cache cleared");
    }

    // Alias for clear() to match Redis convention
    public void flushAll() {
        clear();
    }

    // Alias for ttl() to match test expectations
    public long getTTL(String key) {
        return ttl(key);
    }

    // Alias for expire() to match test expectations
    public boolean setExpire(String key, long ttlSeconds) {
        return expire(key, ttlSeconds);
    }

    public CacheStats getStats() {
        long totalHits = 0;
        long totalMisses = 0;
        long totalEvictions = 0;
        long totalMemory = 0;
        int totalSize = 0;

        for (CacheSegment segment : segments) {
            totalHits += segment.getHitCount();
            totalMisses += segment.getMissCount();
            totalEvictions += segment.getEvictionCount();
            totalMemory += segment.getMemoryUsage();
            totalSize += segment.size();
        }

        return new CacheStats(totalHits, totalMisses, totalEvictions, totalMemory, totalSize);
    }

    public List<String> keys() {
        List<String> allKeys = new ArrayList<>();
        for (CacheSegment segment : segments) {
            allKeys.addAll(segment.getEntries().keySet());
        }
        return allKeys;
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Cache shutdown complete");
    }

    private CacheSegment getSegment(String key) {
        int hash = hash(key);
        return segments[hash & segmentMask];
    }

    private int hash(String key) {
        int h = key.hashCode();
        // Spread bits to reduce collisions
        h ^= (h >>> 16);
        return h;
    }

    private void cleanupExpired() {
        int cleaned = 0;
        for (CacheSegment segment : segments) {
            for (var entry : segment.getEntries().entrySet()) {
                if (entry.getValue().isExpired()) {
                    segment.remove(entry.getKey());
                    cleaned++;
                }
            }
        }
        if (cleaned > 0) {
            logger.debug("Cleaned up {} expired entries", cleaned);
        }
    }

    private EvictionPolicy createPolicy(EvictionPolicy template) {
        // Create new instance of the same policy type
        if (template instanceof LRUEvictionPolicy) {
            return new LRUEvictionPolicy();
        }
        // Add other policy types as needed
        return new LRUEvictionPolicy();
    }

    private static int nextPowerOfTwo(int n) {
        if (n <= 0)
            return 1;
        n--;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n + 1;
    }

    public static class CacheStats {
        public final long hits;
        public final long misses;
        public final long evictions;
        public final long memoryBytes;
        public final int size;

        public CacheStats(long hits, long misses, long evictions, long memoryBytes, int size) {
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.memoryBytes = memoryBytes;
            this.size = size;
        }

        public double getHitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }
    }
}
