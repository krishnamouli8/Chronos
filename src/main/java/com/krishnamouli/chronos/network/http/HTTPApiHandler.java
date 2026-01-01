package com.krishnamouli.chronos.network.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.monitoring.CacheHealthMonitor;
import com.krishnamouli.chronos.monitoring.MetricsCollector;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP REST API handler for monitoring and management.
 * Provides endpoints for health, metrics, and statistics.
 */
public class HTTPApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(HTTPApiHandler.class);

    private final ChronosCache cache;
    private final MetricsCollector metrics;
    private final CacheHealthMonitor healthMonitor;
    private final ObjectMapper objectMapper;

    public HTTPApiHandler(ChronosCache cache, MetricsCollector metrics, CacheHealthMonitor healthMonitor) {
        this.cache = cache;
        this.metrics = metrics;
        this.healthMonitor = healthMonitor;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        HttpMethod method = request.method();

        try {
            FullHttpResponse response;

            if (method == HttpMethod.GET && uri.startsWith("/health")) {
                response = handleHealth();
            } else if (method == HttpMethod.GET && uri.startsWith("/metrics")) {
                response = handleMetrics();
            } else if (method == HttpMethod.GET && uri.startsWith("/stats")) {
                response = handleStats();
            } else {
                response = createNotFoundResponse();
            }

            sendResponse(ctx, response);

        } catch (Exception e) {
            logger.error("Error handling HTTP request: {}", uri, e);
            sendResponse(ctx, createErrorResponse(e.getMessage()));
        }
    }

    private FullHttpResponse handleHealth() throws Exception {
        CacheHealthMonitor.HealthReport report = healthMonitor.getLastReport();

        Map<String, Object> data = new HashMap<>();
        data.put("score", report.getScore());
        // Score >70 indicates healthy cache performance
        data.put("status",
                report.getScore() > com.krishnamouli.chronos.config.CacheConfiguration.HEALTHY_SCORE_THRESHOLD
                        ? "healthy"
                        : "degraded");
        data.put("issues", report.getIssues());

        return createJsonResponse(HttpResponseStatus.OK, data);
    }

    private FullHttpResponse handleMetrics() throws Exception {
        MetricsCollector.MetricsSnapshot snapshot = metrics.getSnapshot();

        // Prometheus-compatible format
        StringBuilder prometheus = new StringBuilder();
        prometheus.append("# HELP chronos_hits_total Total cache hits\n");
        prometheus.append("# TYPE chronos_hits_total counter\n");
        prometheus.append(String.format("chronos_hits_total %d\n", snapshot.hits));

        prometheus.append("# HELP chronos_misses_total Total cache misses\n");
        prometheus.append("# TYPE chronos_misses_total counter\n");
        prometheus.append(String.format("chronos_misses_total %d\n", snapshot.misses));

        prometheus.append("# HELP chronos_hit_rate Current hit rate\n");
        prometheus.append("# TYPE chronos_hit_rate gauge\n");
        prometheus.append(String.format("chronos_hit_rate %.4f\n", snapshot.hitRate));

        prometheus.append("# HELP chronos_memory_bytes Current memory usage\n");
        prometheus.append("# TYPE chronos_memory_bytes gauge\n");
        prometheus.append(String.format("chronos_memory_bytes %d\n", snapshot.memoryBytes));

        prometheus.append("# HELP chronos_latency_milliseconds Latency percentiles\n");
        prometheus.append("# TYPE chronos_latency_milliseconds summary\n");
        prometheus
                .append(String.format("chronos_latency_milliseconds{quantile=\"0.5\"} %.4f\n", snapshot.p50LatencyMs));
        prometheus
                .append(String.format("chronos_latency_milliseconds{quantile=\"0.95\"} %.4f\n", snapshot.p95LatencyMs));
        prometheus
                .append(String.format("chronos_latency_milliseconds{quantile=\"0.99\"} %.4f\n", snapshot.p99LatencyMs));

        return createTextResponse(HttpResponseStatus.OK, prometheus.toString());
    }

    private FullHttpResponse handleStats() throws Exception {
        MetricsCollector.MetricsSnapshot snapshot = metrics.getSnapshot();

        Map<String, Object> data = new HashMap<>();
        data.put("hits", snapshot.hits);
        data.put("misses", snapshot.misses);
        data.put("hitRate", snapshot.hitRate);
        data.put("evictions", snapshot.evictions);
        data.put("memoryBytes", snapshot.memoryBytes);
        data.put("keys", snapshot.size);
        data.put("latency", Map.of(
                "p50Ms", snapshot.p50LatencyMs,
                "p95Ms", snapshot.p95LatencyMs,
                "p99Ms", snapshot.p99LatencyMs));

        return createJsonResponse(HttpResponseStatus.OK, data);
    }

    private FullHttpResponse createJsonResponse(HttpResponseStatus status, Object data) throws Exception {
        String json = objectMapper.writeValueAsString(data);
        byte[] content = json.getBytes(StandardCharsets.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(content));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

        return response;
    }

    private FullHttpResponse createTextResponse(HttpResponseStatus status, String text) {
        byte[] content = text.getBytes(StandardCharsets.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(content));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

        return response;
    }

    private FullHttpResponse createNotFoundResponse() {
        return createTextResponse(HttpResponseStatus.NOT_FOUND, "Not Found");
    }

    private FullHttpResponse createErrorResponse(String message) {
        return createTextResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Error: " + message);
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpResponse response) {
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("HTTP handler exception", cause);
        ctx.close();
    }
}
