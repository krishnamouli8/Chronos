package com.krishnamouli.chronos.intelligence.ttl;

import com.krishnamouli.chronos.core.CacheEntry;
import com.krishnamouli.chronos.core.ChronosCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Adaptive TTL manager using cost-benefit analysis.
 * Dynamically adjusts TTL based on access patterns, data size, and volatility.
 */
public class AdaptiveTTLManager {
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveTTLManager.class);

    private static final long BASE_TTL_SECONDS = com.krishnamouli.chronos.config.CacheConfiguration.BASE_TTL_SECONDS;
    private static final double MIN_MULTIPLIER = com.krishnamouli.chronos.config.CacheConfiguration.MIN_TTL_MULTIPLIER;
    private static final double MAX_MULTIPLIER = com.krishnamouli.chronos.config.CacheConfiguration.MAX_TTL_MULTIPLIER;
    private static final double ADJUSTMENT_THRESHOLD = com.krishnamouli.chronos.config.CacheConfiguration.TTL_ADJUSTMENT_THRESHOLD;

    private final ChronosCache cache;
    private final VolatilityEstimator volatilityEstimator;
    private final ScheduledExecutorService scheduler;

    public AdaptiveTTLManager(ChronosCache cache, long adjustmentIntervalSeconds) {
        this.cache = cache;
        this.volatilityEstimator = new VolatilityEstimator();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chronos-ttl-adjuster");
            t.setDaemon(true);
            return t;
        });

        if (adjustmentIntervalSeconds > 0) {
            scheduler.scheduleAtFixedRate(
                    this::adjustAllTTLs,
                    adjustmentIntervalSeconds,
                    adjustmentIntervalSeconds,
                    TimeUnit.SECONDS);
            logger.info("Adaptive TTL manager started: interval={}s", adjustmentIntervalSeconds);
        }
    }

    public long calculateOptimalTTL(String key, CacheEntry entry) {
        double accessRate = entry.getAccessesPerHour();
        long dataSize = entry.getSize();
        long computeCost = entry.getComputeCost();
        double volatility = volatilityEstimator.getVolatility(key);

        // Cost-benefit calculation
        double benefitScore = accessRate * (computeCost > 0 ? computeCost : 1);
        double costScore = dataSize * (volatility > 0 ? volatility : 0.5);

        if (costScore == 0) {
            costScore = 1; // Prevent division by zero
        }

        double multiplier = benefitScore / costScore;
        multiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));

        long optimalTTL = (long) (BASE_TTL_SECONDS * multiplier);

        logger.trace("TTL calculation for {}: accessRate={}, size={}, cost={}, volatility={} => TTL={}s",
                key, accessRate, dataSize, computeCost, volatility, optimalTTL);

        return optimalTTL;
    }

    public void trackDataChange(String key, int oldHash, int newHash) {
        if (oldHash != newHash) {
            volatilityEstimator.recordChange(key);
        }
    }

    public void shutdown() {
        logger.info("Adaptive TTL manager shutting down");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void adjustAllTTLs() {
        List<String> keys = cache.keys();
        int adjusted = 0;

        for (String key : keys) {
            CacheEntry entry = cache.getEntry(key);
            if (entry == null || entry.isExpired()) {
                continue;
            }

            long currentTTL = entry.getTTL();
            long optimalTTL = calculateOptimalTTL(key, entry);

            // Adjust if difference is significant
            if (Math.abs(optimalTTL - currentTTL) > currentTTL * ADJUSTMENT_THRESHOLD) {
                cache.expire(key, optimalTTL);
                adjusted++;
                logger.debug("Adjusted TTL for {}: {}s -> {}s", key, currentTTL, optimalTTL);
            }
        }

        if (adjusted > 0) {
            logger.info("Adjusted TTL for {} keys", adjusted);
        }
    }

    /**
     * Tracks data volatility (change frequency) for keys.
     */
    private static class VolatilityEstimator {
        private final Map<String, ChangeHistory> histories = new ConcurrentHashMap<>();
        // Max history size balances memory usage vs accuracy of volatility estimates
        private static final int MAX_HISTORY = com.krishnamouli.chronos.config.CacheConfiguration.VOLATILITY_MAX_HISTORY;

        public void recordChange(String key) {
            histories.computeIfAbsent(key, k -> new ChangeHistory())
                    .recordChange();
        }

        public double getVolatility(String key) {
            ChangeHistory history = histories.get(key);
            if (history == null) {
                return 0.5; // Unknown, assume moderate
            }
            return history.calculateVolatility();
        }

        private static class ChangeHistory {
            private final List<Long> changeTimestamps = new CopyOnWriteArrayList<>();

            public void recordChange() {
                changeTimestamps.add(System.currentTimeMillis());
                if (changeTimestamps.size() > MAX_HISTORY) {
                    changeTimestamps.remove(0);
                }
            }

            public double calculateVolatility() {
                if (changeTimestamps.size() < 2) {
                    return 0.5; // Not enough data
                }

                // Calculate average time between changes
                long totalDiff = 0;
                for (int i = 1; i < changeTimestamps.size(); i++) {
                    totalDiff += changeTimestamps.get(i) - changeTimestamps.get(i - 1);
                }

                long avgTimeBetweenChanges = totalDiff / (changeTimestamps.size() - 1);

                // Volatility = changes per hour
                return 3600_000.0 / Math.max(avgTimeBetweenChanges, 1);
            }
        }
    }
}
