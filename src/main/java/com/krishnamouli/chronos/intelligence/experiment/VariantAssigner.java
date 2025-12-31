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
        if (Math.abs(sum - 1.0) > 0.01) {
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
        double normalized = (double) (Math.abs(hash) % 10000) / 10000.0;

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
