package com.krishnamouli.chronos.core;

import com.krishnamouli.chronos.core.eviction.EvictionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Individual cache segment with independent locking for concurrency.
 * Each segment manages its own subset of keys to minimize lock contention.
 */
public class CacheSegment {
    private static final Logger logger = LoggerFactory.getLogger(CacheSegment.class);

    private final Map<String, CacheEntry> entries;
    private final ReadWriteLock lock;
    private final EvictionPolicy evictionPolicy;
    private final long maxMemoryBytes;
    private final AtomicLong currentMemoryBytes;
    private final AtomicLong hitCount;
    private final AtomicLong missCount;
    private final AtomicLong evictionCount;

    public CacheSegment(EvictionPolicy evictionPolicy, long maxMemoryBytes) {
        this.entries = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.evictionPolicy = evictionPolicy;
        this.maxMemoryBytes = maxMemoryBytes;
        this.currentMemoryBytes = new AtomicLong(0);
        this.hitCount = new AtomicLong(0);
        this.missCount = new AtomicLong(0);
        this.evictionCount = new AtomicLong(0);
    }

    public CacheEntry get(String key) {
        lock.readLock().lock();
        try {
            CacheEntry entry = entries.get(key);
            if (entry == null || entry.isExpired()) {
                if (entry != null) {
                    removeExpired(key, entry);
                }
                missCount.incrementAndGet();
                return null;
            }

            evictionPolicy.onAccess(key, entry);
            hitCount.incrementAndGet();
            return entry;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(String key, CacheEntry entry) {
        lock.writeLock().lock();
        try {
            // Remove existing entry if present
            CacheEntry existing = entries.get(key);
            if (existing != null) {
                currentMemoryBytes.addAndGet(-existing.getSize());
                evictionPolicy.onRemove(key);
            }

            // Evict if necessary to make room
            while (currentMemoryBytes.get() + entry.getSize() > maxMemoryBytes && !entries.isEmpty()) {
                evict();
            }

            // Add new entry
            entries.put(key, entry);
            currentMemoryBytes.addAndGet(entry.getSize());
            evictionPolicy.onAdd(key, entry);

        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean remove(String key) {
        lock.writeLock().lock();
        try {
            CacheEntry entry = entries.remove(key);
            if (entry != null) {
                currentMemoryBytes.addAndGet(-entry.getSize());
                evictionPolicy.onRemove(key);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            entries.clear();
            currentMemoryBytes.set(0);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        return entries.size();
    }

    public long getMemoryUsage() {
        return currentMemoryBytes.get();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getEvictionCount() {
        return evictionCount.get();
    }

    public Map<String, CacheEntry> getEntries() {
        return entries;
    }

    private void evict() {
        String victimKey = evictionPolicy.selectVictim(entries);
        if (victimKey != null) {
            CacheEntry victim = entries.remove(victimKey);
            if (victim != null) {
                currentMemoryBytes.addAndGet(-victim.getSize());
                evictionPolicy.onRemove(victimKey);
                evictionCount.incrementAndGet();
                logger.debug("Evicted key: {}", victimKey);
            }
        }
    }

    private void removeExpired(String key, CacheEntry entry) {
        lock.writeLock().lock();
        try {
            entries.remove(key);
            currentMemoryBytes.addAndGet(-entry.getSize());
            evictionPolicy.onRemove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
