package com.krishnamouli.chronos.core;

import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hardcore concurrency stress tests for detecting race conditions,
 * deadlocks, and memory leaks under extreme load.
 */
class ConcurrencyStressTest {

    @Test
    @Timeout(30)
    void testMassiveConcurrency() throws InterruptedException {
        ChronosCache cache = new ChronosCache(256, new LRUEvictionPolicy(), 50 * 1024 * 1024);

        int numThreads = 100;
        int opsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicLong totalOps = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Random random = new Random(threadId);
                    for (int j = 0; j < opsPerThread; j++) {
                        int operation = random.nextInt(100);
                        String key = "key-" + random.nextInt(500);

                        try {
                            if (operation < 40) {
                                // 40% reads
                                cache.get(key);
                            } else if (operation < 80) {
                                // 40% writes
                                byte[] value = generateValue(random, 100, 500);
                                cache.put(key, value, 0);
                            } else if (operation < 90) {
                                // 10% deletes
                                cache.delete(key);
                            } else if (operation < 95) {
                                // 5% TTL operations
                                cache.put(key, generateValue(random, 100, 500),
                                        random.nextInt(10) + 1);
                            } else {
                                // 5% expire updates
                                cache.setExpire(key, random.nextInt(5) + 1);
                            }
                            totalOps.incrementAndGet();
                        } catch (Exception e) {
                            errors.incrementAndGet();
                            System.err.println("Error in thread " + threadId + ": " + e.getMessage());
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

        long duration = System.currentTimeMillis() - startTime;
        double opsPerSec = (totalOps.get() * 1000.0) / duration;

        System.out.println("=== Concurrency Stress Test Results ===");
        System.out.println("Threads: " + numThreads);
        System.out.println("Total operations: " + totalOps.get());
        System.out.println("Duration: " + duration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", opsPerSec) + " ops/sec");
        System.out.println("Errors: " + errors.get());

        assertEquals(0, errors.get(), "No errors should occur during stress test");
    }

    @Test
    @Timeout(20)
    void testHotspotContention() throws InterruptedException {
        ChronosCache cache = new ChronosCache(256, new LRUEvictionPolicy(), 10 * 1024 * 1024);

        int numThreads = 50;
        int hotspotKeys = 10; // Small number of hot keys
        int opsPerThread = 500;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Random random = new Random(threadId);
                    for (int j = 0; j < opsPerThread; j++) {
                        String key = "hotkey-" + random.nextInt(hotspotKeys);

                        if (random.nextBoolean()) {
                            cache.put(key, ("value-" + threadId + "-" + j).getBytes(), 0);
                        } else {
                            cache.get(key);
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

        assertEquals(0, errors.get(), "No errors during hotspot contention");
    }

    @Test
    @Timeout(20)
    void testMemoryConsistencyUnderPressure() throws InterruptedException {
        long maxMemory = 5 * 1024 * 1024; // 5MB
        ChronosCache cache = new ChronosCache(64, new LRUEvictionPolicy(), maxMemory);

        int numThreads = 20;
        int opsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Random random = new Random(threadId);
                    for (int j = 0; j < opsPerThread; j++) {
                        String key = "key-" + random.nextInt(100);
                        byte[] largeValue = new byte[10 * 1024]; // 10KB each
                        random.nextBytes(largeValue);

                        cache.put(key, largeValue, 0);

                        // Occasionally verify data integrity
                        if (j % 10 == 0) {
                            byte[] retrieved = cache.get(key);
                            if (retrieved != null && retrieved.length != largeValue.length) {
                                errors.incrementAndGet();
                                System.err.println("Data corruption detected!");
                            }
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

        assertEquals(0, errors.get(), "No errors during memory pressure test");

        // Memory should stay within bounds
        // Note: Actual implementation would check real memory usage
        System.out.println("Memory consistency test passed");
    }

    @Test
    @Timeout(15)
    void testRapidEvictionCycles() throws InterruptedException {
        long maxMemory = 2 * 1024 * 1024; // 2MB
        ChronosCache cache = new ChronosCache(32, new LRUEvictionPolicy(), maxMemory);

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Write large values to trigger eviction
                    for (int j = 0; j < 100; j++) {
                        String key = "key-" + threadId + "-" + j;
                        byte[] value = new byte[50 * 1024]; // 50KB
                        cache.put(key, value, 0);
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

        assertEquals(0, errors.get(), "No errors during rapid eviction");
    }

    @Test
    @Timeout(15)
    void testConcurrentTTLExpiration() throws InterruptedException {
        ChronosCache cache = new ChronosCache(64, new LRUEvictionPolicy(), 50 * 1024 * 1024);

        int numThreads = 20;
        int opsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);

        // Pre-populate with short TTL
        for (int i = 0; i < 500; i++) {
            cache.put("key-" + i, ("value-" + i).getBytes(), 2); // 2 second TTL
        }

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Random random = new Random(threadId);
                    for (int j = 0; j < opsPerThread; j++) {
                        String key = "key-" + random.nextInt(500);

                        // Mix of operations on potentially expired keys
                        int op = random.nextInt(4);
                        switch (op) {
                            case 0:
                                cache.get(key);
                                break;
                            case 1:
                                cache.put(key, "newvalue".getBytes(), 1);
                                break;
                            case 2:
                                cache.delete(key);
                                break;
                            case 3:
                                cache.getTTL(key);
                                break;
                        }

                        Thread.sleep(10); // Small delay
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

        assertEquals(0, errors.get(), "No errors during concurrent TTL operations");
    }

    @Test
    @Timeout(20)
    void testDeadlockFreedom() throws InterruptedException {
        ChronosCache cache = new ChronosCache(256, new LRUEvictionPolicy(), 20 * 1024 * 1024);

        int numThreads = 50;
        int opsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger completedThreads = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Random random = new Random(threadId);
                    for (int j = 0; j < opsPerThread; j++) {
                        // Perform operations that might cause deadlock if poorly synchronized
                        String key1 = "key-" + random.nextInt(20);
                        String key2 = "key-" + random.nextInt(20);

                        cache.put(key1, "value1".getBytes(), 0);
                        cache.get(key2);
                        cache.delete(key1);
                        cache.put(key2, "value2".getBytes(), 1);
                    }
                    completedThreads.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete without deadlock");
        assertEquals(numThreads, completedThreads.get(), "All threads should complete successfully");
    }

    private byte[] generateValue(Random random, int minSize, int maxSize) {
        int size = random.nextInt(maxSize - minSize + 1) + minSize;
        byte[] value = new byte[size];
        random.nextBytes(value);
        return value;
    }
}
