package com.krishnamouli.chronos.benchmarks;

import com.krishnamouli.chronos.core.ChronosCache;
import com.krishnamouli.chronos.core.eviction.LRUEvictionPolicy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Chronos cache performance.
 * Run with: mvn test -Pbenchmark
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class ChronosBenchmark {

    private ChronosCache cache;
    private static final int NUM_KEYS = 10000;

    @Setup(Level.Trial)
    public void setup() {
        // 100MB cache with 256 segments
        cache = new ChronosCache(256, new LRUEvictionPolicy(), 100 * 1024 * 1024);

        // Pre-populate with 10K entries
        for (int i = 0; i < NUM_KEYS; i++) {
            String key = "key-" + i;
            byte[] value = ("value-data-" + i + "-" + generateRandomString(100)).getBytes(StandardCharsets.UTF_8);
            cache.put(key, value, 0);
        }
    }

    @Benchmark
    public void benchmarkRead(Blackhole bh) {
        String key = "key-" + ThreadLocalRandom.current().nextInt(NUM_KEYS);
        byte[] value = cache.get(key);
        bh.consume(value);
    }

    @Benchmark
    public void benchmarkWrite(Blackhole bh) {
        int keyNum = ThreadLocalRandom.current().nextInt(NUM_KEYS);
        String key = "key-" + keyNum;
        byte[] value = ("new-value-" + keyNum).getBytes(StandardCharsets.UTF_8);
        cache.put(key, value, 0);
        bh.consume(key);
    }

    @Benchmark
    public void benchmarkMixed80Read20Write(Blackhole bh) {
        int rand = ThreadLocalRandom.current().nextInt(100);
        String key = "key-" + ThreadLocalRandom.current().nextInt(NUM_KEYS);

        if (rand < 80) {
            // 80% reads
            byte[] value = cache.get(key);
            bh.consume(value);
        } else {
            // 20% writes
            byte[] value = ("new-value-" + rand).getBytes(StandardCharsets.UTF_8);
            cache.put(key, value, 0);
        }
    }

    @Benchmark
    public void benchmarkWriteHeavy(Blackhole bh) {
        int rand = ThreadLocalRandom.current().nextInt(100);
        String key = "key-" + ThreadLocalRandom.current().nextInt(NUM_KEYS);

        if (rand < 30) {
            // 30% reads
            byte[] value = cache.get(key);
            bh.consume(value);
        } else {
            // 70% writes
            byte[] value = ("new-value-" + rand).getBytes(StandardCharsets.UTF_8);
            cache.put(key, value, 0);
        }
    }

    @Benchmark
    public void benchmarkWithTTL(Blackhole bh) {
        int keyNum = ThreadLocalRandom.current().nextInt(NUM_KEYS);
        String key = "key-" + keyNum;
        byte[] value = ("ttl-value-" + keyNum).getBytes(StandardCharsets.UTF_8);
        cache.put(key, value, 300); // 5 minute TTL
        bh.consume(key);
    }

    @Benchmark
    public void benchmarkDeleteAndReinsert(Blackhole bh) {
        int keyNum = ThreadLocalRandom.current().nextInt(NUM_KEYS);
        String key = "key-" + keyNum;

        cache.delete(key);
        byte[] value = ("reinserted-" + keyNum).getBytes(StandardCharsets.UTF_8);
        cache.put(key, value, 0);
        bh.consume(key);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void benchmarkLatencyRead(Blackhole bh) {
        String key = "key-" + ThreadLocalRandom.current().nextInt(NUM_KEYS);
        byte[] value = cache.get(key);
        bh.consume(value);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void benchmarkLatencyWrite(Blackhole bh) {
        int keyNum = ThreadLocalRandom.current().nextInt(NUM_KEYS);
        String key = "key-" + keyNum;
        byte[] value = ("latency-test-" + keyNum).getBytes(StandardCharsets.UTF_8);
        cache.put(key, value, 0);
        bh.consume(key);
    }

    @Benchmark
    @Threads(4)
    public void benchmarkConcurrent4Threads(Blackhole bh) {
        int rand = ThreadLocalRandom.current().nextInt(100);
        String key = "key-" + ThreadLocalRandom.current().nextInt(NUM_KEYS);

        if (rand < 80) {
            byte[] value = cache.get(key);
            bh.consume(value);
        } else {
            byte[] value = ("concurrent-" + rand).getBytes(StandardCharsets.UTF_8);
            cache.put(key, value, 0);
        }
    }

    @Benchmark
    @Threads(16)
    public void benchmarkConcurrent16Threads(Blackhole bh) {
        int rand = ThreadLocalRandom.current().nextInt(100);
        String key = "key-" + ThreadLocalRandom.current().nextInt(NUM_KEYS);

        if (rand < 80) {
            byte[] value = cache.get(key);
            bh.consume(value);
        } else {
            byte[] value = ("concurrent-" + rand).getBytes(StandardCharsets.UTF_8);
            cache.put(key, value, 0);
        }
    }

    @Benchmark
    public void benchmarkHotspot() {
        // Test hotspot contention - small key set
        String key = "hotkey-" + ThreadLocalRandom.current().nextInt(10);

        if (ThreadLocalRandom.current().nextBoolean()) {
            cache.get(key);
        } else {
            cache.put(key, "hotvalue".getBytes(StandardCharsets.UTF_8), 0);
        }
    }

    @Benchmark
    public void benchmarkLargeValues(Blackhole bh) {
        int keyNum = ThreadLocalRandom.current().nextInt(1000);
        String key = "large-" + keyNum;

        // 10KB values
        byte[] value = new byte[10 * 1024];
        cache.put(key, value, 0);

        byte[] retrieved = cache.get(key);
        bh.consume(retrieved);
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
