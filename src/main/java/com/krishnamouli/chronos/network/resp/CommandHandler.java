package com.krishnamouli.chronos.network.resp;

import com.krishnamouli.chronos.core.ChronosCache;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles Redis protocol commands and executes them against the cache.
 */
public class CommandHandler extends SimpleChannelInboundHandler<List<Object>> {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);

    private final ChronosCache cache;

    public CommandHandler(ChronosCache cache) {
        this.cache = cache;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, List<Object> msg) throws Exception {
        if (msg == null || msg.isEmpty()) {
            ctx.writeAndFlush(new RESPEncoder.RESPError("ERR empty command"));
            return;
        }

        String command = bytesToString((byte[]) msg.get(0)).toUpperCase();

        try {
            Object response = executeCommand(command, msg);
            ctx.writeAndFlush(response);
        } catch (Exception e) {
            logger.error("Error executing command: {}", command, e);
            ctx.writeAndFlush(new RESPEncoder.RESPError("ERR " + e.getMessage()));
        }
    }

    private Object executeCommand(String command, List<Object> args) {
        switch (command) {
            case "PING":
                return handlePing(args);
            case "GET":
                return handleGet(args);
            case "SET":
                return handleSet(args);
            case "DEL":
                return handleDel(args);
            case "EXPIRE":
                return handleExpire(args);
            case "TTL":
                return handleTTL(args);
            case "KEYS":
                return handleKeys(args);
            case "FLUSHALL":
                return handleFlushAll();
            case "INFO":
                return handleInfo();
            default:
                return new RESPEncoder.RESPError("ERR unknown command '" + command + "'");
        }
    }

    private Object handlePing(List<Object> args) {
        if (args.size() > 1) {
            return args.get(1); // Return the message
        }
        return "PONG";
    }

    private Object handleGet(List<Object> args) {
        if (args.size() < 2) {
            return new RESPEncoder.RESPError("ERR wrong number of arguments for 'get' command");
        }

        String key = bytesToString((byte[]) args.get(1));
        byte[] value = cache.get(key);
        return value != null ? value : null; // null will be encoded as $-1\r\n
    }

    private Object handleSet(List<Object> args) {
        if (args.size() < 3) {
            return new RESPEncoder.RESPError("ERR wrong number of arguments for 'set' command");
        }

        String key = bytesToString((byte[]) args.get(1));
        byte[] value = (byte[]) args.get(2);

        long ttl = 0; // No TTL by default

        // Parse optional EX (seconds) or PX (milliseconds)
        for (int i = 3; i < args.size(); i += 2) {
            if (i + 1 < args.size()) {
                String option = bytesToString((byte[]) args.get(i)).toUpperCase();
                if ("EX".equals(option)) {
                    ttl = Long.parseLong(bytesToString((byte[]) args.get(i + 1)));
                } else if ("PX".equals(option)) {
                    ttl = Long.parseLong(bytesToString((byte[]) args.get(i + 1))) / 1000;
                }
            }
        }

        cache.put(key, value, ttl);
        return "OK";
    }

    private Object handleDel(List<Object> args) {
        if (args.size() < 2) {
            return new RESPEncoder.RESPError("ERR wrong number of arguments for 'del' command");
        }

        int deleted = 0;
        for (int i = 1; i < args.size(); i++) {
            String key = bytesToString((byte[]) args.get(i));
            if (cache.delete(key)) {
                deleted++;
            }
        }

        return (long) deleted;
    }

    private Object handleExpire(List<Object> args) {
        if (args.size() < 3) {
            return new RESPEncoder.RESPError("ERR wrong number of arguments for 'expire' command");
        }

        String key = bytesToString((byte[]) args.get(1));
        long seconds = Long.parseLong(bytesToString((byte[]) args.get(2)));

        boolean set = cache.expire(key, seconds);
        return set ? 1L : 0L;
    }

    private Object handleTTL(List<Object> args) {
        if (args.size() < 2) {
            return new RESPEncoder.RESPError("ERR wrong number of arguments for 'ttl' command");
        }

        String key = bytesToString((byte[]) args.get(1));
        return cache.ttl(key);
    }

    private Object handleKeys(List<Object> args) {
        if (args.size() < 2) {
            return new RESPEncoder.RESPError("ERR wrong number of arguments for 'keys' command");
        }

        String pattern = bytesToString((byte[]) args.get(1));
        List<String> keys = cache.keys();

        // Simple pattern matching (only support * for now)
        if ("*".equals(pattern)) {
            List<Object> result = new ArrayList<>(keys);
            return result;
        }

        // Pattern matching would go here
        List<Object> filtered = new ArrayList<>();
        for (String key : keys) {
            if (matchesPattern(key, pattern)) {
                filtered.add(key.getBytes(StandardCharsets.UTF_8));
            }
        }
        return filtered;
    }

    private Object handleFlushAll() {
        cache.clear();
        return "OK";
    }

    private Object handleInfo() {
        ChronosCache.CacheStats stats = cache.getStats();
        String info = String.format(
                "# Cache Stats\r\n" +
                        "hits:%d\r\n" +
                        "misses:%d\r\n" +
                        "hit_rate:%.2f\r\n" +
                        "evictions:%d\r\n" +
                        "memory_bytes:%d\r\n" +
                        "keys:%d\r\n",
                stats.hits, stats.misses, stats.getHitRate() * 100,
                stats.evictions, stats.memoryBytes, stats.size);
        return info.getBytes(StandardCharsets.UTF_8);
    }

    private boolean matchesPattern(String key, String pattern) {
        // Simple glob pattern matching
        return key.matches(pattern.replace("*", ".*").replace("?", "."));
    }

    private String bytesToString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception in command handler", cause);
        ctx.close();
    }
}
