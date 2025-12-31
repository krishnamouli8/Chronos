package com.krishnamouli.chronos.config;

/**
 * Centralized configuration for Chronos cache system.
 */
public class ChronosConfig {

    // Server configuration
    private int redisPort = 6380;
    private int httpPort = 8080;
    private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;

    // Cache configuration
    private long maxMemoryBytes = 1024L * 1024 * 1024; // 1GB default
    private int numSegments = 256;
    private String evictionPolicy = "LRU";
    private long defaultTTLSeconds = 3600;

    // Intelligence features
    private boolean enablePrefetching = true;
    private double prefetchConfidence = 0.7;
    private int prefetchWindowSize = 10;
    private int prefetchThreads = 4;

    private boolean enableAdaptiveTTL = true;
    private long ttlAdjustmentIntervalSeconds = 300;

    // Persistence configuration
    private boolean enableSnapshots = true;
    private long snapshotIntervalSeconds = 3600;
    private String snapshotPath = "./data/chronos.snapshot";

    // Monitoring configuration
    private boolean enableHealthMonitor = true;
    private long healthCheckIntervalSeconds = 60;

    // Getters and setters
    public int getRedisPort() {
        return redisPort;
    }

    public void setRedisPort(int redisPort) {
        this.redisPort = redisPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public void setMaxMemoryBytes(long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public int getNumSegments() {
        return numSegments;
    }

    public void setNumSegments(int numSegments) {
        this.numSegments = numSegments;
    }

    public String getEvictionPolicy() {
        return evictionPolicy;
    }

    public void setEvictionPolicy(String evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    public long getDefaultTTLSeconds() {
        return defaultTTLSeconds;
    }

    public void setDefaultTTLSeconds(long defaultTTLSeconds) {
        this.defaultTTLSeconds = defaultTTLSeconds;
    }

    public boolean isEnablePrefetching() {
        return enablePrefetching;
    }

    public void setEnablePrefetching(boolean enablePrefetching) {
        this.enablePrefetching = enablePrefetching;
    }

    public double getPrefetchConfidence() {
        return prefetchConfidence;
    }

    public void setPrefetchConfidence(double prefetchConfidence) {
        this.prefetchConfidence = prefetchConfidence;
    }

    public int getPrefetchWindowSize() {
        return prefetchWindowSize;
    }

    public void setPrefetchWindowSize(int prefetchWindowSize) {
        this.prefetchWindowSize = prefetchWindowSize;
    }

    public int getPrefetchThreads() {
        return prefetchThreads;
    }

    public void setPrefetchThreads(int prefetchThreads) {
        this.prefetchThreads = prefetchThreads;
    }

    public boolean isEnableAdaptiveTTL() {
        return enableAdaptiveTTL;
    }

    public void setEnableAdaptiveTTL(boolean enableAdaptiveTTL) {
        this.enableAdaptiveTTL = enableAdaptiveTTL;
    }

    public long getTtlAdjustmentIntervalSeconds() {
        return ttlAdjustmentIntervalSeconds;
    }

    public void setTtlAdjustmentIntervalSeconds(long ttlAdjustmentIntervalSeconds) {
        this.ttlAdjustmentIntervalSeconds = ttlAdjustmentIntervalSeconds;
    }

    public boolean isEnableSnapshots() {
        return enableSnapshots;
    }

    public void setEnableSnapshots(boolean enableSnapshots) {
        this.enableSnapshots = enableSnapshots;
    }

    public long getSnapshotIntervalSeconds() {
        return snapshotIntervalSeconds;
    }

    public void setSnapshotIntervalSeconds(long snapshotIntervalSeconds) {
        this.snapshotIntervalSeconds = snapshotIntervalSeconds;
    }

    public String getSnapshotPath() {
        return snapshotPath;
    }

    public void setSnapshotPath(String snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public boolean isEnableHealthMonitor() {
        return enableHealthMonitor;
    }

    public void setEnableHealthMonitor(boolean enableHealthMonitor) {
        this.enableHealthMonitor = enableHealthMonitor;
    }

    public long getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }

    public void setHealthCheckIntervalSeconds(long healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }

    @Override
    public String toString() {
        return String.format(
                "ChronosConfig{redisPort=%d, httpPort=%d, maxMemory=%dMB, segments=%d, eviction=%s}",
                redisPort, httpPort, maxMemoryBytes / (1024 * 1024), numSegments, evictionPolicy);
    }
}
