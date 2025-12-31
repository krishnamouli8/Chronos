package com.krishnamouli.chronos.core.eviction;

import com.krishnamouli.chronos.core.CacheEntry;

import java.util.Map;

/**
 * Least Frequently Used (LFU) eviction policy.
 * Evicts entries with the lowest access count.
 */
public class LFUEvictionPolicy implements EvictionPolicy {

    @Override
    public String selectVictim(Map<String, CacheEntry> entries) {
        if (entries.isEmpty()) {
            return null;
        }

        String victimKey = null;
        long lowestCount = Long.MAX_VALUE;
        long oldestAccess = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
            long count = entry.getValue().getAccessCount();
            long lastAccess = entry.getValue().getLastAccessTime();

            // Tie-breaker: if counts equal, use LRU
            if (count < lowestCount || (count == lowestCount && lastAccess < oldestAccess)) {
                lowestCount = count;
                oldestAccess = lastAccess;
                victimKey = entry.getKey();
            }
        }

        return victimKey;
    }

    @Override
    public void onAccess(String key, CacheEntry entry) {
        // Access count is already updated in CacheEntry
    }

    @Override
    public void onAdd(String key, CacheEntry entry) {
        // No additional tracking needed
    }

    @Override
    public void onRemove(String key) {
        // No cleanup needed
    }
}
