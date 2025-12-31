package com.krishnamouli.chronos.storage;

import com.krishnamouli.chronos.core.CacheEntry;
import com.krishnamouli.chronos.core.ChronosCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Manages RDB-style snapshot persistence with compression and atomic writes.
 */
public class SnapshotManager {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotManager.class);
    private static final int MAGIC = 0x4348524F; // "CHRO" in ASCII
    private static final int VERSION = 1;

    private final ChronosCache cache;
    private final String snapshotPath;
    private final ScheduledExecutorService scheduler;

    public SnapshotManager(ChronosCache cache, String snapshotPath, long intervalSeconds) {
        this.cache = cache;
        this.snapshotPath = snapshotPath;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chronos-snapshot");
            t.setDaemon(true);
            return t;
        });

        if (intervalSeconds > 0) {
            scheduler.scheduleAtFixedRate(
                    this::saveSnapshot,
                    intervalSeconds,
                    intervalSeconds,
                    TimeUnit.SECONDS);
            logger.info("Snapshot scheduler started: interval={}s, path={}", intervalSeconds, snapshotPath);
        }
    }

    public void saveSnapshot() {
        try {
            Path tempPath = Paths.get(snapshotPath + ".tmp");
            Path finalPath = Paths.get(snapshotPath);

            // Create parent directories if needed
            Files.createDirectories(tempPath.getParent());

            // Write to temporary file first
            try (FileOutputStream fos = new FileOutputStream(tempPath.toFile());
                    GZIPOutputStream gzos = new GZIPOutputStream(fos);
                    DataOutputStream dos = new DataOutputStream(gzos)) {

                // Write header
                dos.writeInt(MAGIC);
                dos.writeInt(VERSION);
                dos.writeLong(System.currentTimeMillis());

                // Write cache entries
                List<String> keys = cache.keys();
                dos.writeInt(keys.size());

                int written = 0;
                for (String key : keys) {
                    CacheEntry entry = cache.getEntry(key);
                    if (entry != null && !entry.isExpired()) {
                        writeEntry(dos, key, entry);
                        written++;
                    }
                }

                logger.info("Snapshot saved: {} keys, path={}", written, snapshotPath);
            }

            // Atomic rename
            Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            logger.error("Failed to save snapshot", e);
        }
    }

    public int loadSnapshot() {
        Path path = Paths.get(snapshotPath);
        if (!Files.exists(path)) {
            logger.info("No snapshot file found, starting with empty cache");
            return 0;
        }

        try (FileInputStream fis = new FileInputStream(path.toFile());
                GZIPInputStream gzis = new GZIPInputStream(fis);
                DataInputStream dis = new DataInputStream(gzis)) {

            // Read and validate header
            int magic = dis.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid snapshot file: bad magic number");
            }

            int version = dis.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported snapshot version: " + version);
            }

            long timestamp = dis.readLong();
            int count = dis.readInt();

            // Load entries
            int loaded = 0;
            for (int i = 0; i < count; i++) {
                try {
                    String key = dis.readUTF();
                    byte[] value = new byte[dis.readInt()];
                    dis.readFully(value);
                    long ttl = dis.readLong();

                    cache.put(key, value, ttl);
                    loaded++;
                } catch (IOException e) {
                    logger.warn("Failed to load entry {}/{}", i + 1, count, e);
                }
            }

            logger.info("Snapshot loaded: {} keys from timestamp {}", loaded, timestamp);
            return loaded;

        } catch (IOException e) {
            logger.error("Failed to load snapshot", e);
            return 0;
        }
    }

    public void shutdown() {
        logger.info("Snapshot manager shutting down");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Final snapshot on shutdown
        saveSnapshot();
    }

    private void writeEntry(DataOutputStream dos, String key, CacheEntry entry) throws IOException {
        dos.writeUTF(key);
        byte[] value = entry.getValue();
        dos.writeInt(value.length);
        dos.write(value);
        dos.writeLong(entry.getTTL());
    }
}
