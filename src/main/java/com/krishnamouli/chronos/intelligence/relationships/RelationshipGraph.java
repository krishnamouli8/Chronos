package com.krishnamouli.chronos.intelligence.relationships;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe graph structure for tracking key relationships.
 * Uses adjacency list representation with atomic counters for thread safety.
 */
public class RelationshipGraph {
    private final Map<String, Map<String, AtomicLong>> adjacencyList;
    private final Map<String, AtomicLong> accessCounts;
    private final int maxRelationshipsPerKey;

    public RelationshipGraph(int maxRelationshipsPerKey) {
        this.adjacencyList = new ConcurrentHashMap<>();
        this.accessCounts = new ConcurrentHashMap<>();
        this.maxRelationshipsPerKey = maxRelationshipsPerKey;
    }

    public void recordAccess(String key) {
        accessCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordCoAccess(String key1, String key2) {
        if (key1.equals(key2)) {
            return;
        }

        // Bidirectional relationship
        incrementEdge(key1, key2);
        incrementEdge(key2, key1);
    }

    public Set<String> getRelatedKeys(String key, double threshold) {
        Map<String, AtomicLong> edges = adjacencyList.get(key);
        if (edges == null || edges.isEmpty()) {
            return Collections.emptySet();
        }

        long totalAccess = accessCounts.getOrDefault(key, new AtomicLong(0)).get();
        if (totalAccess == 0) {
            return Collections.emptySet();
        }

        Set<String> related = new HashSet<>();
        for (Map.Entry<String, AtomicLong> entry : edges.entrySet()) {
            double strength = (double) entry.getValue().get() / totalAccess;
            if (strength >= threshold) {
                related.add(entry.getKey());
            }
        }

        return related;
    }

    public double getRelationshipStrength(String key1, String key2) {
        Map<String, AtomicLong> edges = adjacencyList.get(key1);
        if (edges == null) {
            return 0.0;
        }

        AtomicLong coAccessCount = edges.get(key2);
        if (coAccessCount == null) {
            return 0.0;
        }

        long totalAccess = accessCounts.getOrDefault(key1, new AtomicLong(0)).get();
        return totalAccess == 0 ? 0.0 : (double) coAccessCount.get() / totalAccess;
    }

    public void pruneRelationships(String key) {
        Map<String, AtomicLong> edges = adjacencyList.get(key);
        if (edges == null || edges.size() <= maxRelationshipsPerKey) {
            return;
        }

        // Keep only top N relationships by count
        List<Map.Entry<String, AtomicLong>> sorted = new ArrayList<>(edges.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));

        Map<String, AtomicLong> pruned = new ConcurrentHashMap<>();
        for (int i = 0; i < maxRelationshipsPerKey && i < sorted.size(); i++) {
            Map.Entry<String, AtomicLong> entry = sorted.get(i);
            pruned.put(entry.getKey(), entry.getValue());
        }

        adjacencyList.put(key, pruned);
    }

    public int getRelationshipCount() {
        return adjacencyList.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    private void incrementEdge(String from, String to) {
        adjacencyList
                .computeIfAbsent(from, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(to, k -> new AtomicLong(0))
                .incrementAndGet();

        // Prune if needed
        Map<String, AtomicLong> edges = adjacencyList.get(from);
        if (edges.size() > maxRelationshipsPerKey * 1.2) { // 20% overflow before pruning
            pruneRelationships(from);
        }
    }
}
