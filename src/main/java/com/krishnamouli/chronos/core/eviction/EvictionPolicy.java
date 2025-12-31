package com.krishnamouli.chronos.core.eviction;

import com.krishnamouli.chronos.core.CacheEntry;

import java.util.Map;

/**
 * Strategy interface for cache eviction policies.
 */
public interface EvictionPolicy {

    /**
     * Select an entry to evict from the segment.
     * 
     * @param entries Current entries in the cache segment
     * @return Key of entry to evict, or null if no eviction needed
     */
    String selectVictim(Map<String, CacheEntry> entries);

    /**
     * Notify policy that an entry was accessed.
     */
    void onAccess(String key, CacheEntry entry);

    /**
     * Notify policy that an entry was added.
     */
    void onAdd(String key, CacheEntry entry);

    /**
     * Notify policy that an entry was removed.
     */
    void onRemove(String key);
}
