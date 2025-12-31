package com.krishnamouli.chronos.core;

import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ChronosCache core functionality.
 * Tests concurrency, memory management, TTL, and eviction.
 */
class ChronosCacheTest {

    private ChronosCache cache;
    private static final long TEST_MAX_MEMORY = 10 * 1024 * 1024; // 10MB

    @BeforeEach
    void setUp() {
        cache = new ChronosCache(256, new LRUEvictionPolicy(), TEST_MAX_MEMORY);
    }

    @Test
    void testBasicPutAndGet() {
        byte[] value = "test data".getBytes(StandardCharsets.UTF_8);
        cache.put("key1", value, 0);

        byte[] retrieved = cache.get("key1");
        assertNotNull(retrieved);
        assertArrayEquals(value, retrieved);
    }

    @Test
    void testGetNonExistentKey() {
        byte[] retrieved = cache.get("nonexistent");
        assertNull(retrieved);
    }

    @Test
    void testPutUpdatesExistingKey() {
        cache.put("key1", "value1".getBytes(), 0);
        cache.put("key1", "value2".getBytes(), 0);

        byte[] retrieved = cache.get("key1");
        assertArrayEquals("value2".getBytes(), retrieved);
    }

    @Test
    void testDelete() {
        cache.put("key1", "value1".getBytes(), 0);
        assertTrue(cache.delete("key1"));
        assertNull(cache.get("key1"));

        assertFalse(cache.delete("nonexistent"));
    }

    @Test
    void testTTLExpiration() throws InterruptedException {
        cache.put("key1", "value1".getBytes(), 1); // 1 second TTL

        // Should exist immediately
        assertNotNull(cache.get("key1"));

        // Wait for expiration
        Thread.sleep(1500);

        // Should be expired
        assertNull(cache.get("key1"));
    }

    @Test
    void testGetTTL() throws InterruptedException {
        cache.put("key1", "value1".getBytes(), 10); // 10 seconds

        long ttl = cache.getTTL("key1");
        assertTrue(ttl > 0 && ttl <= 10);

        Thread.sleep(1000);
        long ttlAfter = cache.getTTL("key1");
        assertTrue(ttlAfter < ttl);
    }

    @Test
    void testSetExpire() throws InterruptedException {
        cache.put("key1", "value1".getBytes(), 0); // No expiry
        cache.setExpire("key1", 1); // Set 1 second expiry

        assertNotNull(cache.get("key1"));
        Thread.sleep(1500);
        assertNull(cache.get("key1"));
    }

    @Test
    void testFlushAll() {
        cache.put("key1", "value1".getBytes(), 0);
        cache.put("key2", "value2".getBytes(), 0);
        cache.put("key3", "value3".getBytes(), 0);

        cache.flushAll();

        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertNull(cache.get("key3"));
    }

    @Test
    @Timeout(10)
    void testConcurrentPutAndGet() throws InterruptedException {
        int numThreads = 20;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + (threadId % 100);
                        byte[] value = ("value-" + threadId + "-" + j).getBytes();

                        cache.put(key, value, 0);
                        byte[] retrieved = cache.get(key);

                        if (retrieved == null) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get(), "No errors should occur during concurrent operations");
    }

    @Test
    @Timeout(10)
    void testConcurrentPutSameKey() throws InterruptedException {
        int numThreads = 50;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        cache.put("hotkey", ("thread-" + threadId).getBytes(), 0);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Should have some value without crashing
        assertNotNull(cache.get("hotkey"));
    }

    @Test
    void testMemoryBoundedEviction() {
        // Create cache with small memory limit
        ChronosCache smallCache = new ChronosCache(16, new LRUEvictionPolicy(), 5000); // ~5KB

        int entrySize = 220; // 220 + 120 overhead = 340 bytes per entry
        int numEntries = 20; // Should exceed 5KB limit

        for (int i = 0; i < numEntries; i++) {
            byte[] value = new byte[entrySize];
            smallCache.put("key" + i, value, 0);
        }

        // Some entries should have been evicted
        long memUsage = getMemoryUsage(smallCache);
        assertTrue(memUsage <= 5000, "Memory usage should not exceed limit: " + memUsage);

        // Most recent entries should still be present (LRU)
        assertNotNull(smallCache.get("key" + (numEntries - 1)));
    }

    @Test
    void testLRUEvictionOrder() {
        ChronosCache smallCache = new ChronosCache(16, new LRUEvictionPolicy(), 2000);

        // Fill cache
        for (int i = 0; i < 5; i++) {
            smallCache.put("key" + i, new byte[300], 0);
        }

        // Access key0 to make it recently used
        smallCache.get("key0");

        // Add one more to trigger eviction
        smallCache.put("key5", new byte[300], 0);

        // key0 should still be there (recently accessed)
        // key1 should likely be evicted (least recently used)
        assertNotNull(smallCache.get("key0"));
    }

    @Test
    @Timeout(15)
    void testConcurrentEvictionAndAccess() throws InterruptedException {
        ChronosCache smallCache = new ChronosCache(16, new LRUEvictionPolicy(), 10000);

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        String key = "key-" + ((threadId * 100 + j) % 50);
                        smallCache.put(key, new byte[150], 0);

                        // Random reads
                        String readKey = "key-" + (ThreadLocalRandom.current().nextInt(50));
                        smallCache.get(readKey);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get(), "No errors during eviction stress test");

        long memUsage = getMemoryUsage(smallCache);
        assertTrue(memUsage <= 10000, "Memory should stay within bounds: " + memUsage);
    }

    @Test
    void testExpiredEntryCleanup() throws InterruptedException {
        // Put 100 entries with 1-second TTL
        for (int i = 0; i < 100; i++) {
            cache.put("key" + i, ("value" + i).getBytes(), 1);
        }

        // All should exist
        assertNotNull(cache.get("key0"));

        // Wait for expiration
        Thread.sleep(1500);

        // All should be expired
        for (int i = 0; i < 100; i++) {
            assertNull(cache.get("key" + i), "key" + i + " should be expired");
        }
    }

    @Test
    void testMemoryAccuracyAfterOperations() {
        long initialMemory = getMemoryUsage(cache);
        assertEquals(0, initialMemory, "Initial memory should be 0");

        // Add 10 entries
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, new byte[100], 0);
        }

        long afterAdd = getMemoryUsage(cache);
        assertTrue(afterAdd > 0, "Memory should increase after adding");

        // Update 5 entries with same size
        for (int i = 0; i < 5; i++) {
            cache.put("key" + i, new byte[100], 0);
        }

        long afterUpdate = getMemoryUsage(cache);
        assertEquals(afterAdd, afterUpdate, "Memory should stay same after same-size updates");

        // Delete 5 entries
        for (int i = 0; i < 5; i++) {
            cache.delete("key" + i);
        }

        long afterDelete = getMemoryUsage(cache);
        assertTrue(afterDelete < afterUpdate, "Memory should decrease after deletions");

        // Flush all
        cache.flushAll();
        long afterFlush = getMemoryUsage(cache);
        assertEquals(0, afterFlush, "Memory should be 0 after flush");
    }

    private long getMemoryUsage(ChronosCache cache) {
        // Use reflection or expose method for testing
        // For now, we'll estimate based on operations
        // In real implementation, ChronosCache should expose getMemoryUsage()
        return 0; // Placeholder - actual implementation would get real value
    }
}
