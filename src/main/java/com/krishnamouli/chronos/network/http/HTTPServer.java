package com.krishnamouli.chronos.network.http;

import com.krishnamouli.chronos.config.ChronosConfig;
import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.monitoring.CacheHealthMonitor;
import com.krishnamouli.chronos.monitoring.MetricsCollector;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP server for REST API and monitoring dashboard.
 */
public class HTTPServer {
    private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

    private final ChronosConfig config;
    private final ChronosCache cache;
    private final MetricsCollector metrics;
    private final CacheHealthMonitor healthMonitor;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public HTTPServer(
            ChronosConfig config,
            ChronosCache cache,
            MetricsCollector metrics,
            CacheHealthMonitor healthMonitor) {
        this.config = config;
        this.cache = cache;
        this.metrics = metrics;
        this.healthMonitor = healthMonitor;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            // Max request size to prevent DoS
                            pipeline.addLast(new HttpObjectAggregator(
                                    com.krishnamouli.chronos.config.CacheConfiguration.HTTP_MAX_CONTENT_LENGTH));
                            pipeline.addLast(new HTTPApiHandler(cache, metrics, healthMonitor));
                        }
                    });

            ChannelFuture future = bootstrap.bind(config.getHttpPort()).sync();
            serverChannel = future.channel();

            logger.info("HTTP API server started on port {}", config.getHttpPort());
            logger.info("Endpoints: http://localhost:{}/health, /metrics, /stats", config.getHttpPort());

        } catch (InterruptedException e) {
            logger.error("Failed to start HTTP server", e);
            shutdown();
            throw e;
        }
    }

    public void shutdown() {
        logger.info("Shutting down HTTP server...");

        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully().awaitUninterruptibly();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully().awaitUninterruptibly();
        }

        logger.info("HTTP server shutdown complete");
    }
}
