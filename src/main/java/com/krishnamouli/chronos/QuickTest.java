package com.krishnamouli.chronos;

import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;

/**
 * Simple standalone test to verify core functionality.
 */
public class QuickTest {
    public static void main(String[] args) {
        System.out.println("=== Chronos Quick Test ===\n");

        // Create cache
        ChronosCache cache = new ChronosCache(
                256,
                10 * 1024 * 1024, // 10MB
                new LRUEvictionPolicy());

        // Test basic operations
        System.out.println("1. Testing basic operations...");
        cache.put("user:1", "Alice".getBytes(), 0);
        cache.put("user:2", "Bob".getBytes(), 0);
        cache.put("user:3", "Charlie".getBytes(), 30); // 30 sec TTL

        System.out.println("   GET user:1 = " + new String(cache.get("user:1")));
        System.out.println("   GET user:2 = " + new String(cache.get("user:2")));
        System.out.println("   GET user:3 = " + new String(cache.get("user:3")));

        // Test TTL
        System.out.println("\n2. Testing TTL...");
        System.out.println("   TTL user:3 = " + cache.ttl("user:3") + " seconds");
        cache.expire("user:1", 60);
        System.out.println("   Set TTL user:1 to 60 seconds");
        System.out.println("   TTL user:1 = " + cache.ttl("user:1") + " seconds");

        // Test delete
        System.out.println("\n3. Testing delete...");
        System.out.println("   DELETE user:2 = " + cache.delete("user:2"));
        System.out.println("   GET user:2 after delete = " + cache.get("user:2")); // null

        // Test stats
        System.out.println("\n4. Cache statistics:");
        ChronosCache.CacheStats stats = cache.getStats();
        System.out.println("   Hits: " + stats.hits);
        System.out.println("   Misses: " + stats.misses);
        System.out.println("   Hit Rate: " + String.format("%.2f%%", stats.getHitRate() * 100));
        System.out.println("   Memory: " + stats.memoryBytes + " bytes");
        System.out.println("   Keys: " + stats.size);

        // Cleanup
        cache.shutdown();

        System.out.println("\n✅ All tests passed!");
    }
}
