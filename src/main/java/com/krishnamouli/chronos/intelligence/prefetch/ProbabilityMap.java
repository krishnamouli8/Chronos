package com.krishnamouli.chronos.intelligence.prefetch;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Thread-safe probability map for tracking key transition probabilities.
 * Used in Markov chain-based prefetching.
 */
public class ProbabilityMap {
    private final Map<String, AtomicLong> counts;
    private final AtomicLong total;
    private final int maxEntries;

    public ProbabilityMap(int maxEntries) {
        this.counts = new ConcurrentHashMap<>();
        this.total = new AtomicLong(0);
        this.maxEntries = maxEntries;
    }

    public void increment(String key) {
        if (counts.size() >= maxEntries && !counts.containsKey(key)) {
            return; // Prevent unbounded growth
        }

        counts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        total.incrementAndGet();
    }

    public double getProbability(String key) {
        long count = counts.getOrDefault(key, new AtomicLong(0)).get();
        long totalCount = total.get();
        return totalCount == 0 ? 0.0 : (double) count / totalCount;
    }

    public List<Prediction> getTopN(int n, double threshold) {
        long totalCount = total.get();
        if (totalCount == 0) {
            return List.of();
        }

        return counts.entrySet().stream()
                .map(e -> new Prediction(
                        e.getKey(),
                        (double) e.getValue().get() / totalCount))
                .filter(p -> p.probability >= threshold)
                .sorted(Comparator.comparing((Prediction p) -> p.probability).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public int size() {
        return counts.size();
    }

    public static class Prediction {
        public final String key;
        public final double probability;

        public Prediction(String key, double probability) {
            this.key = key;
            this.probability = probability;
        }
    }
}
