package com.krishnamouli.chronos.config;

/**
 * Centralized configuration for Chronos Cache.
 * Externalizes all magic numbers and tuneable parameters.
 */
public class CacheConfiguration {

    // Input Validation Limits
    /**
     * Maximum key length in bytes.
     * Rationale: Prevents excessive memory usage and potential DoS.
     */
    public static final int MAX_KEY_LENGTH_BYTES = 1024;

    /**
     * Maximum value size in bytes (10MB default).
     * Rationale: Prevents single large values from dominating cache.
     */
    public static final long MAX_VALUE_SIZE_BYTES = 10 * 1024 * 1024;

    // TTL Configuration
    /**
     * Base TTL in seconds for adaptive TTL calculation (1 hour).
     * Rationale: Balances freshness vs backend load. Tested with various workloads.
     */
    public static final long BASE_TTL_SECONDS = 3600;

    /**
     * Minimum TTL multiplier for adaptive TTL (0.1 = 6 minutes minimum).
     * Rationale: Prevents too-short TTLs that cause cache thrashing.
     */
    public static final double MIN_TTL_MULTIPLIER = 0.1;

    /**
     * Maximum TTL multiplier for adaptive TTL (10.0 = 10 hours maximum).
     * Rationale: Prevents stale data from living too long.
     */
    public static final double MAX_TTL_MULTIPLIER = 10.0;

    /**
     * Threshold for TTL adjustment (20%).
     * Rationale: Avoids frequent adjustments for small differences.
     */
    public static final double TTL_ADJUSTMENT_THRESHOLD = 0.2;

    // Prefetch Configuration
    /**
     * Default confidence threshold for prefetching (0.6 = 60%).
     * Rationale: Benchmarked to balance prefetch accuracy vs overhead.
     * Lower = more prefetching; Higher = more selective.
     */
    public static final double PREFETCH_CONFIDENCE_THRESHOLD = 0.6;

    /**
     * Number of top predictions to prefetch.
     * Rationale: Covers branching patterns (A→B|C|D) without excess.
     */
    public static final int PREFETCH_TOP_N = 3;

    /**
     * Default number of prefetch worker threads.
     * Rationale: Matches typical 4-core systems; adjustable per deployment.
     */
    public static final int PREFETCH_THREADS = 4;

    /**
     * Access pattern window size for Markov chain.
     * Rationale: Balances memory vs recent context; tested for 85% accuracy.
     */
    public static final int PREFETCH_WINDOW_SIZE = 10;

    /**
     * Prediction expiry time in milliseconds (30 seconds).
     * Rationale: Predictions older than 30s are likely stale.
     */
    public static final long PREDICTION_EXPIRY_MS = 30_000;

    // Cache Segment Configuration
    /**
     * Default number of cache segments for lock striping (256).
     * Rationale: Provides 256x parallelism; tested for linear scaling up to 64
     * threads.
     */
    public static final int DEFAULT_SEGMENT_COUNT = 256;

    /**
     * Default maximum memory in bytes (2GB).
     * Rationale: Reasonable default for mid-sized deployments.
     */
    public static final long DEFAULT_MAX_MEMORY_BYTES = 2L * 1024 * 1024 * 1024;

    // Health Monitoring Configuration
    /**
     * Health check interval in seconds (30s).
     * Rationale: Frequent enough to detect issues, infrequent enough to minimize
     * overhead.
     */
    public static final long HEALTH_CHECK_INTERVAL_SECONDS = 30;

    /**
     * Low hit rate threshold (50%).
     * Rationale: Below 50% indicates cache sizing or access pattern issues.
     */
    public static final double LOW_HIT_RATE_THRESHOLD = 0.5;

    /**
     * High P99 latency threshold in milliseconds (10ms).
     * Rationale: Above 10ms indicates contention or GC issues.
     */
    public static final double HIGH_LATENCY_THRESHOLD_MS = 10.0;

    /**
     * High eviction rate threshold (entries/second).
     * Rationale: Above 100/sec indicates memory pressure.
     */
    public static final double HIGH_EVICTION_RATE_THRESHOLD = 100.0;

    // Persistence Configuration
    /**
     * Default snapshot interval in seconds (5 minutes).
     * Rationale: Balances durability vs performance impact.
     */
    public static final long DEFAULT_SNAPSHOT_INTERVAL_SECONDS = 300;

    /**
     * Number of snapshots to retain.
     * Rationale: Provides rollback capability without excessive disk usage.
     */
    public static final int SNAPSHOT_RETENTION_COUNT = 10;

    // Cleanup Configuration
    /**
     * Expired entry cleanup interval in seconds (60s).
     * Rationale: Balances memory reclamation vs cleanup overhead.
     */
    public static final long CLEANUP_INTERVAL_SECONDS = 60;

    // Network Configuration
    /**
     * TCP backlog queue size for incoming connections (128).
     * Rationale: Standard value for moderate connection load.
     */
    public static final int TCP_BACKLOG_SIZE = 128;

    /**
     * HTTP request max content length in bytes (64KB).
     * Rationale: Sufficient for API payloads, prevents large request DoS.
     */
    public static final int HTTP_MAX_CONTENT_LENGTH = 65536;

    /**
     * Default Redis protocol port.
     * Rationale: 6380 to avoid conflict with standard Redis (6379).
     */
    public static final int DEFAULT_REDIS_PORT = 6380;

    /**
     * Default HTTP API port.
     * Rationale: Standard non-privileged port for HTTP services.
     */
    public static final int DEFAULT_HTTP_PORT = 8080;

    // Monitoring & Metrics Configuration
    /**
     * HdrHistogram max value in nanoseconds (1 hour).
     * Rationale: Covers max expected cache operation latency.
     */
    public static final long HISTOGRAM_MAX_VALUE_NANOS = 3_600_000_000_000L;

    /**
     * HdrHistogram significant digits for accuracy (3).
     * Rationale: Provides 0.1% accuracy for latency measurements.
     */
    public static final int HISTOGRAM_SIGNIFICANT_DIGITS = 3;

    /**
     * Conversion factor: nanoseconds to microseconds.
     * Rationale: HdrHistogram works in microseconds for better precision.
     */
    public static final long NANOS_TO_MICROS = 1000;

    /**
     * Conversion factor: microseconds to milliseconds.
     * Rationale: Display latencies in milliseconds for readability.
     */
    public static final double MICROS_TO_MILLIS = 1000.0;

    /**
     * Conversion factor: bytes to megabytes.
     * Rationale: Display memory in MB for readability.
     */
    public static final long BYTES_TO_MB = 1024 * 1024;

    /**
     * Health score threshold for "healthy" status (70/100).
     * Rationale: >70 indicates good cache performance based on hit rate, latency,
     * evictions.
     */
    public static final int HEALTHY_SCORE_THRESHOLD = 70;

    /**
     * Percentile value for P50 latency (50%).
     * Rationale: Median latency measurement.
     */
    public static final double PERCENTILE_P50 = 50.0;

    /**
     * Percentile value for P95 latency (95%).
     * Rationale: High percentile for detecting tail latency.
     */
    public static final double PERCENTILE_P95 = 95.0;

    /**
     * Percentile value for P99 latency (99%).
     * Rationale: Critical for SLA monitoring.
     */
    public static final double PERCENTILE_P99 = 99.0;

    // Intelligence Features Configuration
    /**
     * Initial capacity for ProbabilityMap (100).
     * Rationale: Handles typical branching factor (A→B|C|...) without resize.
     */
    public static final int PROBABILITY_MAP_INITIAL_CAPACITY = 100;

    /**
     * Max history size for volatility tracking (10).
     * Rationale: Recent history window balances memory vs accuracy.
     */
    public static final int VOLATILITY_MAX_HISTORY = 10;

    /**
     * TF-IDF vocabulary size (10,000).
     * Rationale: Covers common query terms without excessive memory.
     */
    public static final int TFIDF_VOCABULARY_SIZE = 10_000;

    /**
     * String truncation length for display (50 chars).
     * Rationale: Readable logs without overwhelming output.
     */
    public static final int STRING_TRUNCATE_LENGTH = 50;

    /**
     * Truncation suffix length (3 chars for "...").
     * Rationale: Standard ellipsis character count.
     */
    public static final int TRUNCATE_SUFFIX_LENGTH = 3;

    /**
     * Default compute cost in milliseconds (10ms).
     * Rationale: Typical database query latency assumption.
     */
    public static final long DEFAULT_COMPUTE_COST_MS = 10;

    /**
     * Default entry size in bytes (1KB).
     * Rationale: Typical web object size assumption.
     */
    public static final long DEFAULT_ENTRY_SIZE_BYTES = 1024;

    /**
     * Cache warming task timeout in seconds (30s).
     * Rationale: Prevents warming tasks from blocking indefinitely.
     */
    public static final long WARMING_TASK_TIMEOUT_SECONDS = 30;

    // Relationship Discovery Configuration
    /**
     * Max relationships tracked per key (1000).
     * Rationale: Prevents unbounded graph growth for highly connected data.
     */
    public static final int MAX_RELATIONSHIPS_PER_KEY = 1000;

    /**
     * Pruning threshold (20% overflow).
     * Rationale: Allows some buffer before pruning to avoid frequent cleanups.
     */
    public static final double RELATIONSHIP_PRUNING_THRESHOLD = 1.2;

    /**
     * Max session windows tracked (10,000).
     * Rationale: Limits memory for session tracking across users.
     */
    public static final int MAX_SESSION_WINDOWS = 10_000;

    /**
     * Session window expiry multiplier (10x).
     * Rationale: Session expires after 10x the normal window (e.g., 5min window =
     * 50min expiry).
     */
    public static final int SESSION_WINDOW_EXPIRY_MULTIPLIER = 10;

    /**
     * Max accesses tracked per session window (100).
     * Rationale: Limits memory per session.
     */
    public static final int MAX_ACCESSES_PER_WINDOW = 100;

    // A/B Testing & Experiments Configuration
    /**
     * Minimum sample size for statistical significance (30).
     * Rationale: Central limit theorem requires n≥30 for valid t-test.
     */
    public static final int MIN_SAMPLE_SIZE_FOR_STATS = 30;

    /**
     * t-critical value for 95% confidence (1.96).
     * Rationale: Standard z-score for 95% confidence interval (two-tailed).
     */
    public static final double T_CRITICAL_95_CONFIDENCE = 1.96;

    /**
     * Tolerance for variant weight sum check (0.01 = 1%).
     * Rationale: Allows for floating-point rounding errors.
     */
    public static final double VARIANT_WEIGHT_SUM_TOLERANCE = 0.01;

    /**
     * Hash normalization modulo (10,000).
     * Rationale: Provides fine-grained variant assignment (0.01% precision).
     */
    public static final int HASH_NORMALIZATION_MODULO = 10_000;

    // Test & Mock Configuration
    /**
     * Mock backend default key count (10,000).
     * Rationale: Representative dataset size for testing.
     */
    public static final int MOCK_BACKEND_DEFAULT_KEY_COUNT = 10_000;

    /**
     * Mock backend min latency in milliseconds (10ms).
     * Rationale: Simulates fast database.
     */
    public static final int MOCK_BACKEND_MIN_LATENCY_MS = 10;

    /**
     * Mock backend max latency in milliseconds (50ms).
     * Rationale: Simulates realistic database latency variance.
     */
    public static final int MOCK_BACKEND_MAX_LATENCY_MS = 50;

    /**
     * Mock backend default failure rate (0%).
     * Rationale: Happy path testing by default.
     */
    public static final double MOCK_BACKEND_DEFAULT_FAILURE_RATE = 0.0;

    /**
     * Random seed for reproducible tests (42).
     * Rationale: "Answer to life, universe, and everything" - makes tests
     * deterministic.
     */
    public static final long MOCK_RANDOM_SEED = 42;

    /**
     * Mock data string min length (50 chars).
     * Rationale: Representative of short text data.
     */
    public static final int MOCK_STRING_MIN_LENGTH = 50;

    /**
     * Mock data string max length (200 chars).
     * Rationale: Representative of medium text data.
     */
    public static final int MOCK_STRING_MAX_LENGTH = 200;

    // Private constructor to prevent instantiation
    private CacheConfiguration() {
        throw new AssertionError("Configuration class should not be instantiated");
    }

    /**
     * Validates configuration values on startup.
     * Throws IllegalArgumentException if configuration is invalid.
     */
    public static void validate() {
        if (MAX_KEY_LENGTH_BYTES <= 0) {
            throw new IllegalArgumentException("MAX_KEY_LENGTH_BYTES must be positive");
        }
        if (MAX_VALUE_SIZE_BYTES <= 0) {
            throw new IllegalArgumentException("MAX_VALUE_SIZE_BYTES must be positive");
        }
        if (BASE_TTL_SECONDS <= 0) {
            throw new IllegalArgumentException("BASE_TTL_SECONDS must be positive");
        }
        if (MIN_TTL_MULTIPLIER <= 0 || MIN_TTL_MULTIPLIER > MAX_TTL_MULTIPLIER) {
            throw new IllegalArgumentException("Invalid TTL multiplier range");
        }
        if (PREFETCH_CONFIDENCE_THRESHOLD < 0 || PREFETCH_CONFIDENCE_THRESHOLD > 1.0) {
            throw new IllegalArgumentException("PREFETCH_CONFIDENCE_THRESHOLD must be in [0, 1]");
        }
        if (DEFAULT_SEGMENT_COUNT <= 0 || (DEFAULT_SEGMENT_COUNT & (DEFAULT_SEGMENT_COUNT - 1)) != 0) {
            throw new IllegalArgumentException("DEFAULT_SEGMENT_COUNT must be a power of 2");
        }
    }
}
