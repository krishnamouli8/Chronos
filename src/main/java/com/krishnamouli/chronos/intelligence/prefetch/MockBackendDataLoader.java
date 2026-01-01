package com.krishnamouli.chronos.intelligence.prefetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Realistic mock backend data loader for testing and demonstration.
 * Simulates database/API calls with configurable latency and realistic data.
 */
public class MockBackendDataLoader implements PredictivePrefetcher.DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(MockBackendDataLoader.class);

    private final Map<String, byte[]> backendData;
    private final int minLatencyMs;
    private final int maxLatencyMs;
    private final double failureRate;
    private final AtomicInteger loadCount;
    private final AtomicLong totalLatencyMs;

    /**
     * Creates a mock backend with pre-populated data.
     * 
     * @param dataSize     Number of keys to pre-populate
     * @param minLatencyMs Minimum simulated latency in milliseconds
     * @param maxLatencyMs Maximum simulated latency in milliseconds
     * @param failureRate  Probability of load failure (0.0 to 1.0)
     */
    public MockBackendDataLoader(int dataSize, int minLatencyMs, int maxLatencyMs, double failureRate) {
        this.backendData = new ConcurrentHashMap<>();
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.failureRate = Math.max(0.0, Math.min(1.0, failureRate));
        this.loadCount = new AtomicInteger(0);
        this.totalLatencyMs = new AtomicLong(0);

        populateBackend(dataSize);
        logger.info("MockBackendDataLoader initialized with {} keys, latency {}-{}ms, failure rate {}",
                dataSize, minLatencyMs, maxLatencyMs, failureRate);
    }

    /**
     * Creates a mock backend with default settings (10K keys, 10-50ms latency, 0%
     * failure).
     */
    public MockBackendDataLoader() {
        this(
                com.krishnamouli.chronos.config.CacheConfiguration.MOCK_BACKEND_DEFAULT_KEY_COUNT,
                com.krishnamouli.chronos.config.CacheConfiguration.MOCK_BACKEND_MIN_LATENCY_MS,
                com.krishnamouli.chronos.config.CacheConfiguration.MOCK_BACKEND_MAX_LATENCY_MS,
                com.krishnamouli.chronos.config.CacheConfiguration.MOCK_BACKEND_DEFAULT_FAILURE_RATE);
    }

    @Override
    public byte[] load(String key) {
        loadCount.incrementAndGet();

        // Simulate network/database latency
        int latency = ThreadLocalRandom.current().nextInt(minLatencyMs, maxLatencyMs + 1);
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        totalLatencyMs.addAndGet(latency);

        // Simulate failure
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            logger.debug("Simulated load failure for key: {}", key);
            return null;
        }

        // Return data or generate if not exists
        byte[] data = backendData.get(key);
        if (data == null) {
            // Generate data on-the-fly for unknown keys
            data = generateData(key);
            backendData.put(key, data);
        }

        logger.trace("Loaded key '{}' in {}ms", key, latency);
        return data;
    }

    /**
     * Pre-populate backend with realistic data patterns.
     */
    private void populateBackend(int size) {
        // Fixed seed for reproducible tests ("Answer to life, universe, everything")
        Random random = new Random(com.krishnamouli.chronos.config.CacheConfiguration.MOCK_RANDOM_SEED);

        for (int i = 0; i < size; i++) {
            String key = generateKey(i);
            byte[] value = generateData(key, random);
            backendData.put(key, value);
        }
    }

    private String generateKey(int index) {
        // Generate realistic key patterns
        int category = index % 5;
        switch (category) {
            case 0:
                return "user:" + index;
            case 1:
                return "product:" + index;
            case 2:
                return "session:" + index;
            case 3:
                return "order:" + index;
            default:
                return "data:" + index;
        }
    }

    private byte[] generateData(String key) {
        return generateData(key, ThreadLocalRandom.current());
    }

    private byte[] generateData(String key, Random random) {
        // Generate realistic JSON-like data
        String data = String.format(
                "{\"key\":\"%s\",\"timestamp\":%d,\"value\":%d,\"data\":\"%s\"}",
                key,
                System.currentTimeMillis(),
                random.nextInt(com.krishnamouli.chronos.config.CacheConfiguration.MOCK_BACKEND_DEFAULT_KEY_COUNT),
                generateRandomString(random,
                        com.krishnamouli.chronos.config.CacheConfiguration.MOCK_STRING_MIN_LENGTH,
                        com.krishnamouli.chronos.config.CacheConfiguration.MOCK_STRING_MAX_LENGTH));
        return data.getBytes(StandardCharsets.UTF_8);
    }

    private String generateRandomString(Random random, int minLength, int maxLength) {
        int length = random.nextInt(maxLength - minLength + 1) + minLength;
        StringBuilder sb = new StringBuilder(length);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // Metrics methods

    public int getLoadCount() {
        return loadCount.get();
    }

    public long getAverageLatencyMs() {
        int count = loadCount.get();
        return count == 0 ? 0 : totalLatencyMs.get() / count;
    }

    public int getBackendDataSize() {
        return backendData.size();
    }

    /**
     * Update a value in the backend (for testing volatility tracking).
     */
    public void updateValue(String key, byte[] newValue) {
        backendData.put(key, newValue);
    }

    /**
     * Check if a key exists in the backend.
     */
    public boolean hasKey(String key) {
        return backendData.containsKey(key);
    }

    /**
     * Get data directly without simulating latency (for testing).
     */
    public byte[] getDataDirect(String key) {
        return backendData.get(key);
    }
}
