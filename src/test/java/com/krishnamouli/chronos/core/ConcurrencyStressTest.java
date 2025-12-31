package com.krishnamouli.chronos.core;

import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hardcore concurrency stress tests for race conditions and deadlocks.
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
                                cache.get(key);
                            } else if (operation < 80) {
                                byte[] value = new byte[random.nextInt(400) + 100];
                                random.nextBytes(value);
                                cache.put(key, value, 0);
                            } else {
                                cache.delete(key);
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get(), "No errors during stress test");
    }

    @Test
    @Timeout(20)
    void testHotspotContention() throws InterruptedException {
        ChronosCache cache = new ChronosCache(256, new LRUEvictionPolicy(), 10 * 1024 * 1024);

        int numThreads = 50;
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
                        String key = "hotkey-" + random.nextInt(10);

                        if (random.nextBoolean()) {
                            cache.put(key, ("value-" + threadId).getBytes(), 0);
                        } else {
                            cache.get(key);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get(), "No errors during hotspot test");
    }
}
