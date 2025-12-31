package com.krishnamouli.chronos.intelligence.warming;

import com.krishnamouli.chronos.core.CacheEntry;
import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.intelligence.prefetch.PredictivePrefetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Analyzes historical access patterns and intelligently warms the cache
 * with high-value keys after cold start.
 */
public class IntelligentCacheWarmer {
    private static final Logger logger = LoggerFactory.getLogger(IntelligentCacheWarmer.class);

    private final ChronosCache cache;
    private final PredictivePrefetcher.DataLoader dataLoader;
    private final int warmingThreads;
    private final int topN;

    // Cold-start access tracking
    private final Map<String, ColdStartStats> coldStartAccess;
    private volatile long lastStartupTime;

    public IntelligentCacheWarmer(
            ChronosCache cache,
            PredictivePrefetcher.DataLoader dataLoader,
            int warmingThreads,
            int topN) {

        this.cache = cache;
        this.dataLoader = dataLoader;
        this.warmingThreads = warmingThreads;
        this.topN = topN;
        this.coldStartAccess = new ConcurrentHashMap<>();
        this.lastStartupTime = System.currentTimeMillis();

        logger.info("Intelligent cache warmer initialized: threads={}, topN={}",
                warmingThreads, topN);
    }

    public void recordColdStartAccess(String key, long timestamp) {
        // Only track first 5 minutes after startup
        if (timestamp - lastStartupTime < 300_000) {
            coldStartAccess.computeIfAbsent(key, k -> new ColdStartStats())
                    .incrementAccess();
        }
    }

    public List<WarmingRecommendation> analyzeColdStart() {
        logger.info("Analyzing cold-start access patterns...");

        List<WarmingRecommendation> recommendations = new ArrayList<>();

        for (Map.Entry<String, ColdStartStats> entry : coldStartAccess.entrySet()) {
            String key = entry.getKey();
            long frequency = entry.getValue().getAccessCount();

            // Estimate compute cost and size from current cache entry if available
            CacheEntry cacheEntry = cache.getEntry(key);
            long computeCost = cacheEntry != null ? cacheEntry.getComputeCost() : 10; // default 10ms
            long size = cacheEntry != null ? cacheEntry.getSize() : 1024; // default 1KB

            recommendations.add(new WarmingRecommendation(key, frequency, computeCost, size));
        }

        // Sort by priority and take top N
        recommendations.sort(Comparator.naturalOrder());
        List<WarmingRecommendation> topRecommendations = recommendations.stream()
                .limit(topN)
                .collect(Collectors.toList());

        logger.info("Generated {} warming recommendations from {} cold-start keys",
                topRecommendations.size(), coldStartAccess.size());

        return topRecommendations;
    }

    public CompletableFuture<WarmingResult> warmCache() {
        return CompletableFuture.supplyAsync(() -> {
            List<WarmingRecommendation> recommendations = analyzeColdStart();

            if (recommendations.isEmpty()) {
                logger.info("No warming recommendations, cache already warm");
                return new WarmingResult(0, 0, 0);
            }

            logger.info("Starting cache warming with {} keys", recommendations.size());
            long startTime = System.currentTimeMillis();

            ExecutorService warmingPool = Executors.newFixedThreadPool(
                    warmingThreads,
                    r -> {
                        Thread t = new Thread(r, "chronos-warmer");
                        t.setDaemon(true);
                        return t;
                    });

            List<CompletableFuture<Boolean>> tasks = new ArrayList<>();

            for (WarmingRecommendation rec : recommendations) {
                CompletableFuture<Boolean> task = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Check if already in cache
                        if (cache.get(rec.getKey()) != null) {
                            return false; // Already warm
                        }

                        // Load data
                        byte[] data = dataLoader.load(rec.getKey());
                        if (data != null) {
                            cache.put(rec.getKey(), data, 0); // Use default TTL
                            logger.debug("Warmed key: {}", rec.getKey());
                            return true;
                        }
                        return false;
                    } catch (Exception e) {
                        logger.warn("Failed to warm key {}: {}", rec.getKey(), e.getMessage());
                        return false;
                    }
                }, warmingPool);

                tasks.add(task);
            }

            // Wait for all tasks to complete
            int succeeded = 0;
            int failed = 0;
            for (CompletableFuture<Boolean> task : tasks) {
                try {
                    if (task.get(30, TimeUnit.SECONDS)) {
                        succeeded++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                }
            }

            warmingPool.shutdown();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Cache warming complete: {} succeeded, {} failed in {}ms",
                    succeeded, failed, duration);

            return new WarmingResult(succeeded, failed, duration);
        });
    }

    public void resetStartupTime() {
        this.lastStartupTime = System.currentTimeMillis();
        this.coldStartAccess.clear();
        logger.info("Reset startup time for cold-start tracking");
    }

    public static class WarmingResult {
        public final int succeeded;
        public final int failed;
        public final long durationMs;

        public WarmingResult(int succeeded, int failed, long durationMs) {
            this.succeeded = succeeded;
            this.failed = failed;
            this.durationMs = durationMs;
        }
    }

    private static class ColdStartStats {
        private final AtomicLong accessCount = new AtomicLong(0);

        public void incrementAccess() {
            accessCount.incrementAndGet();
        }

        public long getAccessCount() {
            return accessCount.get();
        }
    }
}
