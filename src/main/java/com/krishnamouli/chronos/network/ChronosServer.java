package com.krishnamouli.chronos.network;

import com.krishnamouli.chronos.config.ChronosConfig;
import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.network.resp.CommandHandler;
import com.krishnamouli.chronos.network.resp.RESPDecoder;
import com.krishnamouli.chronos.network.resp.RESPEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance Netty-based server supporting Redis protocol.
 */
public class ChronosServer {
    private static final Logger logger = LoggerFactory.getLogger(ChronosServer.class);

    private final ChronosConfig config;
    private final ChronosCache cache;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ChronosServer(ChronosConfig config, ChronosCache cache) {
        this.config = config;
        this.cache = cache;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // TCP backlog for incoming connections
                    .option(ChannelOption.SO_BACKLOG,
                            com.krishnamouli.chronos.config.CacheConfiguration.TCP_BACKLOG_SIZE)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new RESPDecoder());
                            pipeline.addLast(new RESPEncoder());
                            pipeline.addLast(new CommandHandler(cache));
                        }
                    });

            ChannelFuture future = bootstrap.bind(config.getRedisPort()).sync();
            serverChannel = future.channel();

            logger.info("Chronos Redis protocol server started on port {}", config.getRedisPort());
            logger.info("Connect with: redis-cli -p {}", config.getRedisPort());

        } catch (InterruptedException e) {
            logger.error("Failed to start server", e);
            shutdown();
            throw e;
        }
    }

    public void shutdown() {
        logger.info("Shutting down Chronos server...");

        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully().awaitUninterruptibly();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully().awaitUninterruptibly();
        }

        logger.info("Chronos server shutdown complete");
    }

    public void awaitTermination() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }
}
