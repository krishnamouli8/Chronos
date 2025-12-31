package com.krishnamouli.chronos.intelligence.ttl;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple no-op data loader for testing.
 * In production, this would connect to your data source.
 */
public class NoOpDataLoader implements com.krishnamouli.chronos.intelligence.prefetch.PredictivePrefetcher.DataLoader {
    private final AtomicInteger loadCount = new AtomicInteger(0);

    @Override
    public byte[] load(String key) {
        loadCount.incrementAndGet();
        // In production, this would fetch from database, API, etc.
        return null;
    }

    public int getLoadCount() {
        return loadCount.get();
    }
}
