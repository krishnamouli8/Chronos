package com.krishnamouli.chronos.monitoring;

import com.krishnamouli.chronos.core.ChronosCache;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive metrics collection for cache operations.
 * Uses HdrHistogram for accurate latency tracking.
 */
public class MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private final Histogram latencyHistogram;
    private final AtomicLong totalOperations;
    private final ChronosCache cache;

    public MetricsCollector(ChronosCache cache) {
        this.cache = cache;
        this.latencyHistogram = new Histogram(3600000000L, 3); // 1 hour max, 3 significant digits
        this.totalOperations = new AtomicLong(0);
    }

    public void recordOperation(long latencyNanos) {
        try {
            latencyHistogram.recordValue(latencyNanos / 1000); // Convert to microseconds
            totalOperations.incrementAndGet();
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.warn("Latency value out of bounds: {}ns", latencyNanos);
        }
    }

    public MetricsSnapshot getSnapshot() {
        ChronosCache.CacheStats cacheStats = cache.getStats();

        return new MetricsSnapshot(
                cacheStats.hits,
                cacheStats.misses,
                cacheStats.getHitRate(),
                cacheStats.evictions,
                cacheStats.memoryBytes,
                cacheStats.size,
                latencyHistogram.getValueAtPercentile(50.0) / 1000.0, // P50 in ms
                latencyHistogram.getValueAtPercentile(95.0) / 1000.0, // P95 in ms
                latencyHistogram.getValueAtPercentile(99.0) / 1000.0, // P99 in ms
                totalOperations.get());
    }

    public void reset() {
        latencyHistogram.reset();
        totalOperations.set(0);
    }

    public static class MetricsSnapshot {
        public final long hits;
        public final long misses;
        public final double hitRate;
        public final long evictions;
        public final long memoryBytes;
        public final int size;
        public final double p50LatencyMs;
        public final double p95LatencyMs;
        public final double p99LatencyMs;
        public final long totalOperations;

        public MetricsSnapshot(
                long hits, long misses, double hitRate, long evictions,
                long memoryBytes, int size,
                double p50LatencyMs, double p95LatencyMs, double p99LatencyMs,
                long totalOperations) {

            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
            this.evictions = evictions;
            this.memoryBytes = memoryBytes;
            this.size = size;
            this.p50LatencyMs = p50LatencyMs;
            this.p95LatencyMs = p95LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
            this.totalOperations = totalOperations;
        }
    }
}
