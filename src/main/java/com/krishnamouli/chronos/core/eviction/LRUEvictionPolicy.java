package com.krishnamouli.chronos.core.eviction;

import com.krishnamouli.chronos.core.CacheEntry;

import java.util.Map;

/**
 * Least Recently Used (LRU) eviction policy.
 * Evicts entries that haven't been accessed recently.
 */
public class LRUEvictionPolicy implements EvictionPolicy {

    @Override
    public String selectVictim(Map<String, CacheEntry> entries) {
        if (entries.isEmpty()) {
            return null;
        }

        String victimKey = null;
        long oldestAccess = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
            long lastAccess = entry.getValue().getLastAccessTime();
            if (lastAccess < oldestAccess) {
                oldestAccess = lastAccess;
                victimKey = entry.getKey();
            }
        }

        return victimKey;
    }

    @Override
    public void onAccess(String key, CacheEntry entry) {
        // Access time is already updated in CacheEntry
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
