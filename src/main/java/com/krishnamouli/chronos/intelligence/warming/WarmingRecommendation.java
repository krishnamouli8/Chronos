package com.krishnamouli.chronos.intelligence.warming;

/**
 * Recommendation for cache warming with priority scoring.
 */
public class WarmingRecommendation implements Comparable<WarmingRecommendation> {
    private final String key;
    private final long accessFrequency;
    private final long computeCostMs;
    private final long sizeBytes;
    private final double priority;

    public WarmingRecommendation(String key, long accessFrequency, long computeCostMs, long sizeBytes) {
        this.key = key;
        this.accessFrequency = accessFrequency;
        this.computeCostMs = computeCostMs;
        this.sizeBytes = sizeBytes;
        this.priority = calculatePriority();
    }

    private double calculatePriority() {
        // priority = frequency × compute_cost / size
        // High priority: frequent, expensive to compute, small
        double cost = Math.max(computeCostMs, 1);
        double size = Math.max(sizeBytes, 1);
        return (double) accessFrequency * cost / size;
    }

    public String getKey() {
        return key;
    }

    public long getAccessFrequency() {
        return accessFrequency;
    }

    public long getComputeCostMs() {
        return computeCostMs;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public double getPriority() {
        return priority;
    }

    @Override
    public int compareTo(WarmingRecommendation other) {
        return Double.compare(other.priority, this.priority); // Higher priority first
    }

    @Override
    public String toString() {
        return String.format("WarmingRecommendation{key='%s', freq=%d, cost=%dms, size=%dB, priority=%.2f}",
                key, accessFrequency, computeCostMs, sizeBytes, priority);
    }
}
