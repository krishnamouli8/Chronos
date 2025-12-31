package com.krishnamouli.chronos.intelligence.experiment;

import org.HdrHistogram.Histogram;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks metrics per experiment variant.
 * Collects hit rate, latency, memory, and evictions for statistical analysis.
 */
public class ExperimentTracker {
    private final String variantName;
    private final AtomicLong hits;
    private final AtomicLong misses;
    private final AtomicLong evictions;
    private final Histogram latencyHistogram;
    private final List<Long> memorySamples;

    public ExperimentTracker(String variantName) {
        this.variantName = variantName;
        this.hits = new AtomicLong(0);
        this.misses = new AtomicLong(0);
        this.evictions = new AtomicLong(0);
        this.latencyHistogram = new Histogram(3600000000L, 3);
        this.memorySamples = new ArrayList<>();
    }

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public void recordLatency(long latencyNanos) {
        try {
            latencyHistogram.recordValue(latencyNanos / 1000); // microseconds
        } catch (ArrayIndexOutOfBoundsException e) {
            // Latency too high, ignore
        }
    }

    public synchronized void recordMemory(long bytes) {
        memorySamples.add(bytes);
    }

    public VariantMetrics getMetrics() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;

        double hitRate = total == 0 ? 0.0 : (double) totalHits / total;
        double p50Latency = latencyHistogram.getValueAtPercentile(50.0) / 1000.0; // ms
        double p99Latency = latencyHistogram.getValueAtPercentile(99.0) / 1000.0; // ms

        double avgMemory = memorySamples.isEmpty() ? 0.0
                : memorySamples.stream().mapToLong(Long::longValue).average().orElse(0.0);

        return new VariantMetrics(
                variantName,
                hitRate,
                p50Latency,
                p99Latency,
                evictions.get(),
                avgMemory);
    }

    public static class VariantMetrics {
        public final String variantName;
        public final double hitRate;
        public final double p50LatencyMs;
        public final double p99LatencyMs;
        public final long evictions;
        public final double avgMemoryBytes;

        public VariantMetrics(String variantName, double hitRate, double p50LatencyMs,
                double p99LatencyMs, long evictions, double avgMemoryBytes) {
            this.variantName = variantName;
            this.hitRate = hitRate;
            this.p50LatencyMs = p50LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
            this.evictions = evictions;
            this.avgMemoryBytes = avgMemoryBytes;
        }

        @Override
        public String toString() {
            return String.format("%s: hitRate=%.2f%%, p50=%.2fms, p99=%.2fms, evictions=%d, mem=%.0fMB",
                    variantName, hitRate * 100, p50LatencyMs, p99LatencyMs, evictions, avgMemoryBytes / 1024 / 1024);
        }
    }

    /**
     * Welch's t-test for comparing two variants (unequal variances).
     * Returns true if difference is statistically significant at 95% confidence.
     */
    public static boolean isSignificantDifference(List<Double> sample1, List<Double> sample2) {
        if (sample1.size() < 30 || sample2.size() < 30) {
            return false; // Need sufficient sample size
        }

        double mean1 = sample1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double mean2 = sample2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double var1 = variance(sample1, mean1);
        double var2 = variance(sample2, mean2);

        double se = Math.sqrt(var1 / sample1.size() + var2 / sample2.size());
        if (se == 0) {
            return false;
        }

        double tStat = Math.abs(mean1 - mean2) / se;
        double tCritical = 1.96; // 95% confidence, approximation for large samples

        return tStat > tCritical;
    }

    private static double variance(List<Double> values, double mean) {
        double sumSquares = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSquares += diff * diff;
        }
        return values.size() > 1 ? sumSquares / (values.size() - 1) : 0.0;
    }
}
