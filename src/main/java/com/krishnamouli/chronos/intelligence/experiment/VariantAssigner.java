package com.krishnamouli.chronos.intelligence.experiment;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Consistent hash-based variant assigner for A/B experiments.
 * Ensures same session always gets same variant.
 */
public class VariantAssigner {
    private final Map<String, String> assignments;
    private final String[] variants;
    private final double[] trafficSplit;

    public VariantAssigner(String[] variants, double[] trafficSplit) {
        if (variants.length != trafficSplit.length) {
            throw new IllegalArgumentException("Variants and traffic split must have same length");
        }

        double sum = 0;
        for (double split : trafficSplit) {
            sum += split;
        }
        // Allow for floating-point rounding errors
        if (Math.abs(sum - 1.0) > com.krishnamouli.chronos.config.CacheConfiguration.VARIANT_WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException("Traffic split must sum to 1.0");
        }

        this.variants = variants;
        this.trafficSplit = trafficSplit;
        this.assignments = new ConcurrentHashMap<>();
    }

    public String assign(String sessionId) {
        // Check if already assigned
        String existing = assignments.get(sessionId);
        if (existing != null) {
            return existing;
        }

        // Hash-based deterministic assignment
        int hash = sessionId.hashCode();
        // Normalize hash to 0.0-1.0 range for fine-grained assignment
        double normalized = (double) (Math.abs(hash)
                % com.krishnamouli.chronos.config.CacheConfiguration.HASH_NORMALIZATION_MODULO) /
                (double) com.krishnamouli.chronos.config.CacheConfiguration.HASH_NORMALIZATION_MODULO;

        // Find bucket based on traffic split
        double cumulative = 0.0;
        for (int i = 0; i < variants.length; i++) {
            cumulative += trafficSplit[i];
            if (normalized < cumulative) {
                assignments.put(sessionId, variants[i]);
                return variants[i];
            }
        }

        // Fallback (should not happen)
        String fallback = variants[variants.length - 1];
        assignments.put(sessionId, fallback);
        return fallback;
    }

    public void clearAssignments() {
        assignments.clear();
    }
}
