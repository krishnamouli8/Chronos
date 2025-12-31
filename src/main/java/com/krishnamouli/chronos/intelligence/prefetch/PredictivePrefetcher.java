package com.krishnamouli.chronos.intelligence.prefetch;

import com.krishnamouli.chronos.core.ChronosCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * ML-powered predictive prefetcher using Markov chains.
 * Learns access patterns and preloads data before requests.
 */
public class PredictivePrefetcher {
    private static final Logger logger = LoggerFactory.getLogger(PredictivePrefetcher.class);

    private final ChronosCache cache;
    private final Map<String, ProbabilityMap> transitionMatrix;
    private final Deque<String> recentAccess;
    private final int windowSize;
    private final double confidenceThreshold;
    private final int topN;
    private final ExecutorService prefetchExecutor;
    private final DataLoader dataLoader;

    // Metrics
    private final AtomicInteger predictionsHit = new AtomicInteger(0);
    private final AtomicInteger predictionsMade = new AtomicInteger(0);

    public PredictivePrefetcher(
            ChronosCache cache,
            int windowSize,
            double confidenceThreshold,
            int prefetchThreads,
            DataLoader dataLoader) {

        this.cache = cache;
        this.transitionMatrix = new ConcurrentHashMap<>();
        this.recentAccess = new ConcurrentLinkedDeque<>();
        this.windowSize = windowSize;
        this.confidenceThreshold = confidenceThreshold;
        this.topN = 3;
        this.dataLoader = dataLoader;

        this.prefetchExecutor = Executors.newFixedThreadPool(
                prefetchThreads,
                r -> {
                    Thread t = new Thread(r, "chronos-prefetch");
                    t.setDaemon(true);
                    return t;
                });

        logger.info("Predictive prefetcher initialized: window={}, confidence={}, threads={}",
                windowSize, confidenceThreshold, prefetchThreads);
    }

    public void recordAccess(String key) {
        // Update transition probabilities
        List<String> history = getRecentAccessWindow();
        updateTransitionProbabilities(history, key);

        // Add to recent access
        synchronized (recentAccess) {
            recentAccess.addLast(key);
            if (recentAccess.size() > windowSize) {
                recentAccess.removeFirst();
            }
        }

        // Predict and prefetch
        predictAndPrefetch(key);
    }

    public void onCacheHit(String key) {
        // Check if this was a predicted key
        if (isPredicted(key)) {
            predictionsHit.incrementAndGet();
        }
    }

    public double getPrefetchAccuracy() {
        int made = predictionsMade.get();
        return made == 0 ? 0.0 : (double) predictionsHit.get() / made;
    }

    public void shutdown() {
        logger.info("Prefetch executor shutting down");
        prefetchExecutor.shutdown();
        try {
            if (!prefetchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                prefetchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            prefetchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private List<String> getRecentAccessWindow() {
        synchronized (recentAccess) {
            return new ArrayList<>(recentAccess);
        }
    }

    private void updateTransitionProbabilities(List<String> history, String current) {
        for (String prev : history) {
            transitionMatrix
                    .computeIfAbsent(prev, k -> new ProbabilityMap(100))
                    .increment(current);
        }
    }

    private void predictAndPrefetch(String current) {
        List<String> predictedKeys = predictNextAccess(current);

        if (!predictedKeys.isEmpty()) {
            predictionsMade.addAndGet(predictedKeys.size());
            prefetchInBackground(predictedKeys);

            logger.debug("Predicted {} keys after accessing: {}", predictedKeys.size(), current);
        }
    }

    private List<String> predictNextAccess(String current) {
        ProbabilityMap probabilities = transitionMatrix.get(current);
        if (probabilities == null) {
            return List.of();
        }

        return probabilities.getTopN(topN, confidenceThreshold).stream()
                .map(p -> p.key)
                .collect(Collectors.toList());
    }

    private void prefetchInBackground(List<String> keys) {
        for (String key : keys) {
            prefetchExecutor.submit(() -> {
                try {
                    // Check if already in cache
                    if (cache.get(key) == null) {
                        byte[] data = dataLoader.load(key);
                        if (data != null) {
                            cache.put(key, data, 0); // Use default TTL
                            logger.debug("Prefetched key: {}", key);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to prefetch key: {}", key, e);
                }
            });
        }
    }

    private boolean isPredicted(String key) {
        // Simple check - in production, maintain a predicted keys set
        return true; // Simplified for now
    }

    /**
     * Interface for loading data when not in cache.
     * Application-specific implementation required.
     */
    public interface DataLoader {
        byte[] load(String key);
    }
}
