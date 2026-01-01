package com.krishnamouli.chronos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Audit logger for tracking security-relevant cache operations.
 * Logs authentication attempts, rate limit violations, and administrative
 * actions.
 * 
 * Uses asynchronous logging to avoid impacting cache performance.
 */
public class AuditLogger {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogger.class);

    private final Path auditLogPath;
    private final BlockingQueue<AuditEvent> eventQueue;
    private final ExecutorService logWriter;
    private final ObjectMapper mapper;
    private final boolean enabled;

    /**
     * Create audit logger.
     * 
     * @param auditLogPath Path to audit log file
     * @param enabled      Whether audit logging is enabled
     */
    public AuditLogger(String auditLogPath, boolean enabled) {
        this.enabled = enabled;
        this.auditLogPath = auditLogPath != null ? Paths.get(auditLogPath) : null;
        this.eventQueue = new LinkedBlockingQueue<>(10000); // Buffer up to 10K events
        this.mapper = new ObjectMapper();

        if (enabled && this.auditLogPath != null) {
            // Ensure directory exists
            try {
                Files.createDirectories(this.auditLogPath.getParent());
            } catch (IOException e) {
                logger.warn("Failed to create audit log directory", e);
            }

            // Start async log writer
            this.logWriter = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "audit-logger");
                t.setDaemon(true);
                return t;
            });

            logWriter.submit(this::processLogQueue);
        } else {
            this.logWriter = null;
        }
    }

    /**
     * Log authentication attempt.
     */
    public void logAuthAttempt(String clientId, boolean success, String reason) {
        if (!enabled)
            return;

        AuditEvent event = new AuditEvent(
                "AUTH_ATTEMPT",
                clientId,
                success ? "SUCCESS" : "FAILURE",
                reason);
        eventQueue.offer(event);
    }

    /**
     * Log rate limit violation.
     */
    public void logRateLimitViolation(String clientId, long currentRate) {
        if (!enabled)
            return;

        AuditEvent event = new AuditEvent(
                "RATE_LIMIT_EXCEEDED",
                clientId,
                "BLOCKED",
                "Current rate: " + currentRate);
        eventQueue.offer(event);
    }

    /**
     * Log administrative action (FLUSHALL, etc.).
     */
    public void logAdminAction(String clientId, String action, String details) {
        if (!enabled)
            return;

        AuditEvent event = new AuditEvent(
                "ADMIN_ACTION",
                clientId,
                action,
                details);
        eventQueue.offer(event);
    }

    /**
     * Shutdown audit logger.
     */
    public void shutdown() {
        if (logWriter != null) {
            logWriter.shutdown();
        }
    }

    /**
     * Process queued audit events asynchronously.
     */
    private void processLogQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AuditEvent event = eventQueue.take();
                writeAuditEvent(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Write audit event to log file.
     */
    private void writeAuditEvent(AuditEvent event) {
        try {
            String json = mapper.writeValueAsString(event) + "\n";
            Files.writeString(
                    auditLogPath,
                    json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.error("Failed to write audit event", e);
        }
    }

    /**
     * Audit event data structure.
     */
    public static class AuditEvent {
        public final String timestamp;
        public final String eventType;
        public final String clientId;
        public final String result;
        public final String details;

        public AuditEvent(String eventType, String clientId, String result, String details) {
            this.timestamp = Instant.now().toString();
            this.eventType = eventType;
            this.clientId = clientId;
            this.result = result;
            this.details = details;
        }

        // Getters for Jackson serialization
        public String getTimestamp() {
            return timestamp;
        }

        public String getEventType() {
            return eventType;
        }

        public String getClientId() {
            return clientId;
        }

        public String getResult() {
            return result;
        }

        public String getDetails() {
            return details;
        }
    }
}
