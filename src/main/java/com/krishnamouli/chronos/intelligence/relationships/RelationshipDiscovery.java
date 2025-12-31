package com.krishnamouli.chronos.intelligence.relationships;

import com.krishnamouli.chronos.core.ChronosCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Discovers relationships between cache keys by analyzing co-access patterns.
 * Enables smart cascade invalidation based on learned relationships.
 */
public class RelationshipDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(RelationshipDiscovery.class);

    private final ChronosCache cache;
    private final RelationshipGraph graph;
    private final Map<String, SessionWindow> sessionWindows;
    private final long windowMs;
    private final double cascadeThreshold;

    public RelationshipDiscovery(ChronosCache cache, long windowMs, double cascadeThreshold) {
        this.cache = cache;
        this.graph = new RelationshipGraph(1000); // max 1000 relationships per key
        this.sessionWindows = new ConcurrentHashMap<>();
        this.windowMs = windowMs;
        this.cascadeThreshold = cascadeThreshold;

        logger.info("Relationship discovery initialized: window={}ms, threshold={}",
                windowMs, cascadeThreshold);
    }

    public void trackAccess(String sessionId, String key) {
        long now = System.currentTimeMillis();

        // Record individual access
        graph.recordAccess(key);

        // Get or create session window
        SessionWindow window = sessionWindows.computeIfAbsent(
                sessionId,
                k -> new SessionWindow());

        // Get keys accessed in this session within time window
        Set<String> recentKeys = window.getRecentKeys(now, windowMs);

        // Record co-access with all recent keys
        for (String recentKey : recentKeys) {
            graph.recordCoAccess(key, recentKey);
        }

        // Add current key to window
        window.addAccess(key, now);

        // Clean up old sessions periodically
        if (sessionWindows.size() > 10000) {
            cleanupOldSessions(now);
        }
    }

    public Set<String> getRelatedKeys(String key, double threshold) {
        return graph.getRelatedKeys(key, threshold);
    }

    public double getRelationshipStrength(String key1, String key2) {
        return graph.getRelationshipStrength(key1, key2);
    }

    public void invalidateWithRelated(String key) {
        // Get strongly related keys
        Set<String> related = graph.getRelatedKeys(key, cascadeThreshold);

        // Invalidate primary key
        cache.delete(key);

        // Cascade invalidate related keys
        int cascaded = 0;
        for (String relatedKey : related) {
            if (cache.delete(relatedKey)) {
                cascaded++;
                logger.debug("Cascade invalidated {} (related to {})", relatedKey, key);
            }
        }

        if (cascaded > 0) {
            logger.info("Invalidated {} and {} related keys", key, cascaded);
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("relationshipCount", graph.getRelationshipCount());
        stats.put("activeSessions", sessionWindows.size());
        return stats;
    }

    private void cleanupOldSessions(long now) {
        sessionWindows.entrySet().removeIf(entry -> {
            SessionWindow window = entry.getValue();
            return now - window.getLastAccessTime() > windowMs * 10; // 10x window
        });
    }

    /**
     * Tracks keys accessed within a session time window.
     */
    private static class SessionWindow {
        private final Deque<AccessRecord> accesses;

        public SessionWindow() {
            this.accesses = new ConcurrentLinkedDeque<>();
        }

        public void addAccess(String key, long timestamp) {
            accesses.addLast(new AccessRecord(key, timestamp));

            // Keep window bounded
            if (accesses.size() > 100) {
                accesses.removeFirst();
            }
        }

        public Set<String> getRecentKeys(long now, long windowMs) {
            Set<String> keys = new HashSet<>();
            long cutoff = now - windowMs;

            for (AccessRecord record : accesses) {
                if (record.timestamp >= cutoff) {
                    keys.add(record.key);
                }
            }

            return keys;
        }

        public long getLastAccessTime() {
            AccessRecord last = accesses.peekLast();
            return last != null ? last.timestamp : 0;
        }

        private static class AccessRecord {
            final String key;
            final long timestamp;

            AccessRecord(String key, long timestamp) {
                this.key = key;
                this.timestamp = timestamp;
            }
        }
    }
}
