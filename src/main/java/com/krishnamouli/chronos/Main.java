package com.krishnamouli.chronos;

import com.krishnamouli.chronos.config.ChronosConfig;
import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import com.krishnamouli.chronos.intelligence.prefetch.PredictivePrefetcher;
import com.krishnamouli.chronos.intelligence.ttl.AdaptiveTTLManager;
import com.krishnamouli.chronos.intelligence.ttl.NoOpDataLoader;
import com.krishnamouli.chronos.monitoring.CacheHealthMonitor;
import com.krishnamouli.chronos.monitoring.MetricsCollector;
import com.krishnamouli.chronos.network.ChronosServer;
import com.krishnamouli.chronos.network.http.HTTPServer;
import com.krishnamouli.chronos.storage.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for Chronos - Intelligent Distributed Cache System.
 * Initializes all components: cache, intelligence, persistence, networking,
 * monitoring.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("╔═══════════════════════════════════════════════════╗");
        logger.info("║   Chronos - Intelligent Cache System v1.0        ║");
        logger.info("║   Built-in ML | Adaptive TTL | Real-time Monitoring    ║");
        logger.info("╚═══════════════════════════════════════════════════╝");

        ChronosConfig config = new ChronosConfig();

        // Parse command line arguments
        if (args.length > 0) {
            try {
                config.setRedisPort(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                logger.error("Invalid port number: {}", args[0]);
                System.exit(1);
            }
        }

        logger.info("Configuration: {}", config);

        // Initialize cache
        logger.info("Initializing cache engine...");
        ChronosCache cache = new ChronosCache(
                config.getNumSegments(),
                config.getMaxMemoryBytes(),
                new LRUEvictionPolicy());

        // Initialize monitoring
        logger.info("Initializing monitoring...");
        MetricsCollector metrics = new MetricsCollector(cache);

        CacheHealthMonitor healthMonitor = null;
        if (config.isEnableHealthMonitor()) {
            healthMonitor = new CacheHealthMonitor(
                    cache,
                    metrics,
                    config.getHealthCheckIntervalSeconds());
        }

        // Initialize intelligence features
        PredictivePrefetcher prefetcher = null;
        if (config.isEnablePrefetching()) {
            logger.info("Initializing predictive prefetcher...");
            prefetcher = new PredictivePrefetcher(
                    cache,
                    config.getPrefetchWindowSize(),
                    config.getPrefetchConfidence(),
                    config.getPrefetchThreads(),
                    new NoOpDataLoader() // Replace with real data loader in production
            );
        }

        AdaptiveTTLManager ttlManager = null;
        if (config.isEnableAdaptiveTTL()) {
            logger.info("Initializing adaptive TTL manager...");
            ttlManager = new AdaptiveTTLManager(
                    cache,
                    config.getTtlAdjustmentIntervalSeconds());
        }

        // Initialize snapshot manager
        SnapshotManager snapshotManager = null;
        if (config.isEnableSnapshots()) {
            logger.info("Initializing persistence...");
            snapshotManager = new SnapshotManager(
                    cache,
                    config.getSnapshotPath(),
                    config.getSnapshotIntervalSeconds());
            int loaded = snapshotManager.loadSnapshot();
            if (loaded > 0) {
                logger.info("Restored {} keys from snapshot", loaded);
            }
        }

        // Initialize network servers
        logger.info("Starting network servers...");
        ChronosServer redisServer = new ChronosServer(config, cache);

        HTTPServer httpServer = new HTTPServer(config, cache, metrics, healthMonitor);

        // Graceful shutdown hook
        final PredictivePrefetcher finalPrefetcher = prefetcher;
        final AdaptiveTTLManager finalTTLManager = ttlManager;
        final SnapshotManager finalSnapshotManager = snapshotManager;
        final CacheHealthMonitor finalHealthMonitor = healthMonitor;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, cleaning up...");

            redisServer.shutdown();
            httpServer.shutdown();

            if (finalPrefetcher != null) {
                finalPrefetcher.shutdown();
            }
            if (finalTTLManager != null) {
                finalTTLManager.shutdown();
            }
            if (finalSnapshotManager != null) {
                finalSnapshotManager.shutdown();
            }
            if (finalHealthMonitor != null) {
                finalHealthMonitor.shutdown();
            }

            cache.shutdown();
            logger.info("Chronos shutdown complete");
        }));

        try {
            redisServer.start();
            httpServer.start();

            logger.info("╔═══════════════════════════════════════════════════╗");
            logger.info("║           Chronos is ready!                       ║");
            logger.info("║                                                   ║");
            logger.info("║  Redis Protocol: localhost:{}                  ║", config.getRedisPort());
            logger.info("║  HTTP API:       localhost:{}                  ║", config.getHttpPort());
            logger.info("║                                                   ║");
            logger.info("║  Try: redis-cli -p {}                         ║", config.getRedisPort());
            logger.info("║  Try: curl http://localhost:{}/health        ║", config.getHttpPort());
            logger.info("║                                                   ║");
            logger.info("║  Press Ctrl+C to stop                             ║");
            logger.info("╚═══════════════════════════════════════════════════╝");

            redisServer.awaitTermination();

        } catch (InterruptedException e) {
            logger.error("Server interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Fatal error", e);
            System.exit(1);
        }
    }
}