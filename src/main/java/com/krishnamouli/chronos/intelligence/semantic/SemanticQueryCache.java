package com.krishnamouli.chronos.intelligence.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic query cache using TF-IDF embeddings and cosine similarity.
 * Caches similar queries together to reduce redundant computations.
 */
public class SemanticQueryCache {
    private static final Logger logger = LoggerFactory.getLogger(SemanticQueryCache.class);

    private final Map<String, CachedQuery> exactCache;
    private final TFIDFEmbedder embedder;
    private final double similarityThreshold;
    private final int maxEmbeddings;

    public SemanticQueryCache(double similarityThreshold, int maxEmbeddings) {
        this.exactCache = new ConcurrentHashMap<>();
        // TF-IDF embedder with configurable vocabulary size
        this.embedder = new TFIDFEmbedder(
                com.krishnamouli.chronos.config.CacheConfiguration.TFIDF_VOCABULARY_SIZE);
        this.similarityThreshold = similarityThreshold;
        this.maxEmbeddings = maxEmbeddings;

        logger.info("Semantic query cache initialized: threshold={}, maxEmbeddings={}",
                similarityThreshold, maxEmbeddings);
    }

    public byte[] get(String query) {
        // Fast path: exact match
        CachedQuery cached = exactCache.get(query);
        if (cached != null) {
            cached.recordHit();
            logger.debug("Exact cache hit for query: {}", truncate(query));
            return cached.getData();
        }

        // Slow path: semantic similarity
        Map<String, Double> queryVector = embedder.embed(query);

        String bestMatch = null;
        double bestSimilarity = 0.0;

        for (Map.Entry<String, CachedQuery> entry : exactCache.entrySet()) {
            CachedQuery candidate = entry.getValue();
            double similarity = TFIDFEmbedder.cosineSimilarity(queryVector, candidate.getEmbedding());

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = entry.getKey();
            }
        }

        if (bestSimilarity >= similarityThreshold && bestMatch != null) {
            CachedQuery matched = exactCache.get(bestMatch);
            matched.recordSemanticHit();
            logger.info("Semantic cache hit: similarity={:.3f}, original={}, query={}",
                    bestSimilarity, truncate(bestMatch), truncate(query));
            return matched.getData();
        }

        logger.debug("Cache miss for query: {}", truncate(query));
        return null;
    }

    public void put(String query, byte[] data) {
        // Evict if needed (LRU)
        if (exactCache.size() >= maxEmbeddings) {
            evictLRU();
        }

        // Compute embedding
        Map<String, Double> embedding = embedder.embed(query);
        embedder.updateCorpus(query); // Learn from this query

        exactCache.put(query, new CachedQuery(data, embedding));
        logger.debug("Cached query: {}", truncate(query));
    }

    public void invalidate(String query) {
        exactCache.remove(query);
    }

    public void clear() {
        exactCache.clear();
        logger.info("Semantic cache cleared");
    }

    public Map<String, Object> getStats() {
        long exactHits = exactCache.values().stream()
                .mapToLong(CachedQuery::getExactHits)
                .sum();

        long semanticHits = exactCache.values().stream()
                .mapToLong(CachedQuery::getSemanticHits)
                .sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", exactCache.size());
        stats.put("exactHits", exactHits);
        stats.put("semanticHits", semanticHits);
        stats.put("totalHits", exactHits + semanticHits);
        return stats;
    }

    private void evictLRU() {
        // Find least recently used entry
        String lruKey = null;
        long oldestHit = Long.MAX_VALUE;

        for (Map.Entry<String, CachedQuery> entry : exactCache.entrySet()) {
            long lastHit = entry.getValue().getLastHitTime();
            if (lastHit < oldestHit) {
                oldestHit = lastHit;
                lruKey = entry.getKey();
            }
        }

        if (lruKey != null) {
            exactCache.remove(lruKey);
            logger.debug("Evicted LRU query: {}", truncate(lruKey));
        }
    }

    private String truncate(String str) {
        // Truncate long strings for readable logging
        return str.length() > com.krishnamouli.chronos.config.CacheConfiguration.STRING_TRUNCATE_LENGTH
                ? str.substring(0, com.krishnamouli.chronos.config.CacheConfiguration.STRING_TRUNCATE_LENGTH -
                        com.krishnamouli.chronos.config.CacheConfiguration.TRUNCATE_SUFFIX_LENGTH) + "..."
                : str;
    }

    private static class CachedQuery {
        private final byte[] data;
        private final Map<String, Double> embedding;
        private long exactHits;
        private long semanticHits;
        private long lastHitTime;

        public CachedQuery(byte[] data, Map<String, Double> embedding) {
            this.data = data;
            this.embedding = embedding;
            this.exactHits = 0;
            this.semanticHits = 0;
            this.lastHitTime = System.currentTimeMillis();
        }

        public void recordHit() {
            exactHits++;
            lastHitTime = System.currentTimeMillis();
        }

        public void recordSemanticHit() {
            semanticHits++;
            lastHitTime = System.currentTimeMillis();
        }

        public byte[] getData() {
            return data;
        }

        public Map<String, Double> getEmbedding() {
            return embedding;
        }

        public long getExactHits() {
            return exactHits;
        }

        public long getSemanticHits() {
            return semanticHits;
        }

        public long getLastHitTime() {
            return lastHitTime;
        }
    }
}
