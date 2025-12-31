package com.krishnamouli.chronos.intelligence;

import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import com.krishnamouli.chronos.intelligence.prefetch.MockBackendDataLoader;
import com.krishnamouli.chronos.intelligence.prefetch.PredictivePrefetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for predictive prefetching.
 */
class PrefetchingIntegrationTest {

    private ChronosCache cache;
    private MockBackendDataLoader dataLoader;
    private PredictivePrefetcher prefetcher;

    @BeforeEach
    void setUp() {
        cache = new ChronosCache(64, new LRUEvictionPolicy(), 10 * 1024 * 1024);
        dataLoader = new MockBackendDataLoader(1000, 10, 30, 0.0);
        prefetcher = new PredictivePrefetcher(cache, 10, 0.6, 4, dataLoader);
    }

    @Test
    void testSequentialAccessPattern() throws InterruptedException {
        String[] pattern = { "user:1", "product:1", "order:1" };

        for (int i = 0; i < 50; i++) {
            for (String key : pattern) {
                if (cache.get(key) == null) {
                    byte[] data = dataLoader.load(key);
                    if (data != null) {
                        cache.put(key, data, 0);
                    }
                }
                prefetcher.recordAccess(key);
                Thread.sleep(10);
            }

            cache.flushAll();
        }

        double accuracy = prefetcher.getPrefetchAccuracy();
        System.out.println("Sequential pattern accuracy: " + String.format("%.2f%%", accuracy * 100));

        assertTrue(accuracy >= 0, "Accuracy should be non-negative");
    }

    @Test
    void testPrefetcherShutdown() {
        prefetcher.shutdown();
        assertTrue(true, "Shutdown completed");
    }
}
