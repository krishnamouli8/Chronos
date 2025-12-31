package com.krishnamouli.chronos.core;

import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Ch ronosCache core functionality.
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
        
        assertNotNull(cache.get("key1"));
        
        Thread.sleep(1500);
        
        assertNull(cache.get("key1"));
    }
    
    @Test
    void testGetTTL() throws InterruptedException {
        cache.put("key1", "value1".getBytes(), 10);
        
        long ttl = cache.getTTL("key1");
        assertTrue(ttl > 0 && ttl <= 10);
        
        Thread.sleep(1000);
        long ttlAfter = cache.getTTL("key1");
        assertTrue(ttlAfter < ttl);
    }
    
    @Test
    void testSetExpire() throws InterruptedException {
        cache.put("key1", "value1".getBytes(), 0);
        cache.setExpire("key1", 1);
        
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
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        assertEquals(0, errors.get());
    }
}
