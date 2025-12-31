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
        cache = new ChronosCache(256, new LRUEvictionPolicy(), 100 * 1024 * 1024);
        
        for (int i = 0; i < NUM_KEYS; i++) {
            String key = "key-" + i;
            byte[] value = ("value-" + i).getBytes(StandardCharsets.UTF_8);
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
            byte[] value = cache.get(key);
            bh.consume(value);
        } else {
            byte[] value = ("new-" + rand).getBytes(StandardCharsets.UTF_8);
            cache.put(key, value, 0);
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void benchmarkLatencyRead(Blackhole bh) {
        String key = "key-" + ThreadLocalRandom.current().nextInt(NUM_KEYS);
        byte[] value = cache.get(key);
        bh.consume(value);
    }
    
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
