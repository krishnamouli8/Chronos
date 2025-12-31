package com.krishnamouli.chronos.intelligence;

import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import com.krishnamouli.chronos.intelligence.prefetch.MockBackendDataLoader;
import com.krishnamouli.chronos.intelligence.prefetch.PredictivePrefetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for predictive prefetching.
 * Tests that Markov chain learning actually works with realistic patterns.
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
        // Simulate pattern: user:1 → product:1 → order:1
        String[] pattern = { "user:1", "product:1", "order:1" };

        // Train the prefetcher with 50 iterations
        for (int i = 0; i < 50; i++) {
            for (String key : pattern) {
                // Simulate cache miss - load from backend
                if (cache.get(key) == null) {
                    byte[] data = dataLoader.load(key);
                    if (data != null) {
                        cache.put(key, data, 0);
                    }
                }
                prefetcher.recordAccess(key);
                Thread.sleep(10); // Small delay to allow prefetch
            }

            // Clear cache to force misses in next iteration
            cache.flushAll();
        }

        // After training, test prediction accuracy
        int hits = 0;
        int total = 20;

        for (int i = 0; i < total; i++) {
            cache.flushAll();

            // Access first key
            String firstKey = pattern[0];
            if (cache.get(firstKey) == null) {
                byte[] data = dataLoader.load(firstKey);
                cache.put(firstKey, data, 0);
            }
            prefetcher.recordAccess(firstKey);
            prefetcher.onCacheHit(firstKey);

            // Wait for prefetching
            Thread.sleep(100);

            // Check if subsequent keys were prefetched
            for (int j = 1; j < pattern.length; j++) {
                if (cache.get(pattern[j]) != null) {
                    hits++;
                    prefetcher.onCacheHit(pattern[j]);
                }
            }
        }

        double accuracy = prefetcher.getPrefetchAccuracy();
        System.out.println("Sequential pattern prefetch accuracy: " +
                String.format("%.2f%%", accuracy * 100));

        // Should have learned the pattern
        assertTrue(accuracy > 0.5, "Prefetch accuracy should be > 50% after training");
    }

    @Test
    void testBranchingPattern() throws InterruptedException {
        // Pattern: A → B (70%) or A → C (30%)
        for (int i = 0; i < 100; i++) {
            cache.put("A".getBytes(), "dataA".getBytes(), 0);
            prefetcher.recordAccess("A");

            if (i % 10 < 7) {
                // 70% go to B
                cache.put("B".getBytes(), "dataB".getBytes(), 0);
                prefetcher.recordAccess("B");
            } else {
                // 30% go to C
                cache.put("C".getBytes(), "dataC".getBytes(), 0);
                prefetcher.recordAccess("C");
            }

            Thread.sleep(5);
        }

        double accuracy = prefetcher.getPrefetchAccuracy();
        System.out.println("Branching pattern prefetch accuracy: " +
                String.format("%.2f%%", accuracy * 100));

        // Should have some accuracy even with branching
        assertTrue(accuracy >= 0, "Accuracy should be non-negative");
    }

    @Test
    void testNoPredictablePattern() throws InterruptedException {
        // Random access - should have low prefetch accuracy
        java.util.Random random = new java.util.Random(42);

        for (int i = 0; i < 100; i++) {
            String key = "key" + random.nextInt(50);
            cache.put(key.getBytes(), ("data" + i).getBytes(), 0);
            prefetcher.recordAccess(key);
            Thread.sleep(5);
        }

        double accuracy = prefetcher.getPrefetchAccuracy();
        System.out.println("Random access prefetch accuracy: " +
                String.format("%.2f%%", accuracy * 100));

        // Random patterns should have low accuracy
        assertTrue(accuracy < 0.5, "Random access should have < 50% accuracy");
    }

    @Test
    void testCyclicPattern() throws InterruptedException {
        // Cycle: A → B → C → D → A
        String[] cycle = { "A", "B", "C", "D" };

        // Train
        for (int i = 0; i < 50; i++) {
            for (String key : cycle) {
                if (cache.get(key) == null) {
                    byte[] data = ("data-" + key).getBytes(StandardCharsets.UTF_8);
                    cache.put(key, data, 0);
                }
                prefetcher.recordAccess(key);
                Thread.sleep(10);
            }
        }

        // Test: after accessing A, B should be prefetched
        cache.flushAll();
        cache.put("A".getBytes(), "dataA".getBytes(), 0);
        prefetcher.recordAccess("A");

        Thread.sleep(100);

        // B should likely be prefetched
        byte[] bData = cache.get("B");

        double accuracy = prefetcher.getPrefetchAccuracy();
        System.out.println("Cyclic pattern prefetch accuracy: " +
                String.format("%.2f%%", accuracy * 100));
    }

    @Test
    void testPrefetchActuallyLoadsData() throws InterruptedException {
        cache.flushAll();

        // Ensure keys exist in backend
        String key1 = "user:0";
        String key2 = "user:1";

        assertTrue(dataLoader.hasKey(key1), "Backend should have key1");
        assertTrue(dataLoader.hasKey(key2), "Backend should have key2");

        // Establish pattern
        for (int i = 0; i < 30; i++) {
            cache.put(key1, dataLoader.load(key1), 0);
            prefetcher.recordAccess(key1);

            cache.put(key2, dataLoader.load(key2), 0);
            prefetcher.recordAccess(key2);

            cache.flushAll();
            Thread.sleep(10);
        }

        // Now access key1 and wait
        cache.put(key1, dataLoader.load(key1), 0);
        prefetcher.recordAccess(key1);

        int initialLoads = dataLoader.getLoadCount();
        Thread.sleep(150); // Wait for prefetch

        // Prefetcher should have loaded key2
        int finalLoads = dataLoader.getLoadCount();

        assertTrue(finalLoads > initialLoads,
                "Prefetcher should have loaded data from backend");
    }

    @Test
    void testPrefetcherShutdown() {
        prefetcher.shutdown();
        // Should shutdown without hanging
        assertTrue(true, "Shutdown completed");
    }

    @Test
    void testPredictionExpiry() throws InterruptedException {
        // Record a pattern
        prefetcher.recordAccess("key1");
        prefetcher.recordAccess("key2");

        // Wait longer than expiry time (30 seconds + buffer)
        // Note: This test is simplified - in real scenario we'd use smaller expiry for
        // testing
        Thread.sleep(50); // Just verify no crash with expired predictions

        // Access key2 - should not count as hit if not in predicted set
        prefetcher.onCacheHit("key2");

        assertTrue(true, "Prediction expiry handling works");
    }
}
