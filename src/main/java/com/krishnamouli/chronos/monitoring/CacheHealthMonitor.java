package com.krishnamouli.chronos.monitoring;

import com.krishnamouli.chronos.core.ChronosCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Real-time health monitoring with anomaly detection.
 * Provides actionable recommendations for cache optimization.
 */
public class CacheHealthMonitor {
    private static final Logger logger = LoggerFactory.getLogger(CacheHealthMonitor.class);

    private final ChronosCache cache;
    private final MetricsCollector metrics;
    private final ScheduledExecutorService scheduler;
    private volatile HealthReport lastReport;

    public CacheHealthMonitor(ChronosCache cache, MetricsCollector metrics, long checkIntervalSeconds) {
        this.cache = cache;
        this.metrics = metrics;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chronos-health-monitor");
            t.setDaemon(true);
            return t;
        });

        if (checkIntervalSeconds > 0) {
            scheduler.scheduleAtFixedRate(
                    this::runHealthCheck,
                    checkIntervalSeconds,
                    checkIntervalSeconds,
                    TimeUnit.SECONDS);
            logger.info("Health monitoring started: interval={}s", checkIntervalSeconds);
        }
    }

    public HealthReport diagnose() {
        MetricsCollector.MetricsSnapshot snapshot = metrics.getSnapshot();
        HealthReport report = new HealthReport();

        // Check hit rate
        if (snapshot.hitRate < 0.5) {
            report.addIssue(Severity.HIGH,
                    String.format("Low hit rate (%.1f%%). Consider:\n" +
                            "- Increasing cache size\n" +
                            "- Adjusting eviction policy\n" +
                            "- Enabling predictive prefetching",
                            snapshot.hitRate * 100));
        } else if (snapshot.hitRate > 0.9) {
            report.addIssue(Severity.INFO,
                    String.format("Excellent hit rate (%.1f%%)", snapshot.hitRate * 100));
        }

        // Check latency
        if (snapshot.p99LatencyMs > 10.0) {
            report.addIssue(Severity.MEDIUM,
                    String.format("High P99 latency (%.2fms). Possible causes:\n" +
                            "- Lock contention (check concurrent access)\n" +
                            "- Large values (consider compression)\n" +
                            "- Memory pressure (GC pauses)",
                            snapshot.p99LatencyMs));
        }

        // Check eviction rate
        double evictionRate = calculateEvictionRate(snapshot);
        if (evictionRate > 100) {
            report.addIssue(Severity.HIGH,
                    String.format("High eviction rate (%.1f/sec). Your cache is too small.\n" +
                            "Recommendation: Increase max memory",
                            evictionRate));
        }

        // Calculate health score (0-100)
        int score = calculateHealthScore(snapshot);
        report.setScore(score);

        return report;
    }

    public HealthReport getLastReport() {
        return lastReport != null ? lastReport : diagnose();
    }

    public void shutdown() {
        logger.info("Health monitor shutting down");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void runHealthCheck() {
        try {
            HealthReport report = diagnose();
            lastReport = report;

            if (!report.getIssues().isEmpty()) {
                logger.info("Health check: score={}/100, issues={}",
                        report.getScore(), report.getIssues().size());

                for (HealthIssue issue : report.getIssues()) {
                    if (issue.severity == Severity.HIGH) {
                        logger.warn("Health issue: {}", issue.message);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Health check failed", e);
        }
    }

    private double calculateEvictionRate(MetricsCollector.MetricsSnapshot snapshot) {
        // Simplified: assume 60 second window
        return snapshot.evictions / 60.0;
    }

    private int calculateHealthScore(MetricsCollector.MetricsSnapshot snapshot) {
        int score = 100;

        // Deduct for low hit rate
        if (snapshot.hitRate < 0.5)
            score -= 30;
        else if (snapshot.hitRate < 0.7)
            score -= 15;

        // Deduct for high latency
        if (snapshot.p99LatencyMs > 10)
            score -= 20;
        else if (snapshot.p99LatencyMs > 5)
            score -= 10;

        // Deduct for high eviction rate
        double evictionRate = calculateEvictionRate(snapshot);
        if (evictionRate > 100)
            score -= 25;
        else if (evictionRate > 50)
            score -= 15;

        return Math.max(0, score);
    }

    public static class HealthReport {
        private final List<HealthIssue> issues = new ArrayList<>();
        private int score;

        public void addIssue(Severity severity, String message) {
            issues.add(new HealthIssue(severity, message));
        }

        public void setScore(int score) {
            this.score = score;
        }

        public List<HealthIssue> getIssues() {
            return issues;
        }

        public int getScore() {
            return score;
        }
    }

    public static class HealthIssue {
        public final Severity severity;
        public final String message;

        public HealthIssue(Severity severity, String message) {
            this.severity = severity;
            this.message = message;
        }
    }

    public enum Severity {
        INFO, LOW, MEDIUM, HIGH
    }
}
