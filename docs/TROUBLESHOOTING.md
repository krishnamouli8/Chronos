# Troubleshooting Guide

Common issues, root causes, and solutions for Chronos Cache.

---

## Quick Diagnosis

### Cache is slow

- **Check**: P99 latency in `/health` endpoint
- **If > 10ms**: See [High Latency](#high-latency)

### Low hit rate

- **Check**: Hit rate in `/metrics`
- **If < 50%**: See [Low Hit Rate](#low-hit-rate)

### High memory usage

- **Check**: Memory ratio in `/stats`
- **If > 90%**: See [Memory Issues](#memory-issues)

### Cache crashes

- **Check**: Logs at `/var/log/chronos/chronos.log`
- See [Crash Debugging](#crash-debugging)

---

## Performance Issues

### High Latency

**Symptom**: P99 latency > 10ms

**Diagnosis**:

```bash
# Check latency distribution
curl http://localhost:8080/stats | jq '.performance'

# Check GC pauses
tail -100 /var/log/chronos/gc.log | grep "Pause"

# Check lock contention
jstack <pid> | grep -A 5 "lock"
```

**Common Causes**:

**1. GC Pauses**

```bash
# Evidence: GC log shows > 50ms pauses
-XX:+PrintGCDetails shows: [GC pause (G1 Evacuation Pause) 150ms]

# Solution: Tune GC
-XX:MaxGCPauseMillis=200 \
-XX:G1HeapRegionSize=32m \
-XX:InitiatingHeapOccupancyPercent=35
```

**2. Lock Contention**

```bash
# Evidence: Many threads blocked on locks
jstack shows: waiting to lock <0x...> (ChronosCache)

# Solution: Increase segments (code change)
NUM_SEGMENTS=512  # Currently 256
```

**3. Large Values**

```bash
# Evidence: Values > 1MB
curl http://localhost:8080/stats | jq '.cache.avg_value_size'

# Solution: Compress large values before caching
# Or: Increase value size limit in config
```

---

### Low Hit Rate

**Symptom**: Hit rate < 50%

**Diagnosis**:

```bash
# Check hit rate trend
curl http://localhost:8080/metrics | grep hit_rate

# Check eviction rate
curl http://localhost:8080/metrics | grep evictions_total
```

**Common Causes**:

**1. Cache Too Small**

```bash
# Evidence: High eviction rate (> 100/sec)
chronos_evictions_total increasing rapidly

# Solution: Increase memory
MAX_MEMORY_MB=8192  # Double the size

# Or: Use better eviction policy
EVICTION_POLICY=LFU  # Frequency-based instead of recency
```

**2. Access Pattern Not Cacheable**

```bash
# Evidence: Truly random accesses
# Check if keys have patterns: user:* session:* product:*

# Solution: Review if caching makes sense
# Consider: Time-based caching (cache for 5 min regardless)
```

**3. TTL Too Short**

```bash
# Evidence: Many cache misses but keys existed recently
# Check TTL settings in application

# Solution: Increase TTL or enable adaptive TTL
ADAPTIVE_TTL_ENABLED=true
```

**4. Prefetching Disabled or Ineffective**

```bash
# Check prefetch accuracy
curl http://localhost:8080/stats | jq '.prefetch.accuracy'

# If < 0.6: Pattern not suitable for prefetching
# If disabled: Enable it
curl -X POST http://localhost:8080/admin/prefetch/enable
```

---

## Memory Issues

### OutOfMemoryError

**Symptom**: Chronos crashes with `java.lang.OutOfMemoryError: Java heap space`

**Diagnosis**:

```bash
# Check heap usage before crash
# Look in gc.log for "Full GC" before crash

# Check heap dump (if created with -XX:+HeapDumpOnOutOfMemoryError)
jhat /tmp/java_pid*.hprof
```

**Solutions**:

**1. Increase Heap**

```bash
# Before: -Xmx2g
# After: -Xmx8g

# Restart Chronos with new setting
```

**2. Enable Eviction**

```bash
# Ensure eviction is configured
MAX_MEMORY_MB=4096  # Set hard limit
EVICTION_POLICY=LRU
```

**3. Find Memory Leak**

```bash
# Take heap dump
jmap -dump:live,format=b,file=heap.bin <pid>

# Analyze with Eclipse MAT or VisualVM
# Look for:
# - Large collections that never shrink
# - Transition matrix growing unbounded
# - Old CacheEntry objects not evicted
```

---

### High Memory Usage (not OOM)

**Symptom**: Memory usage > 90% but not crashing

**Diagnosis**:

```bash
# Check breakdown
curl http://localhost:8080/stats | jq '.cache.memory_bytes'

# Check entry count
redis-cli -p 6380 INFO | grep total_keys
```

**Solutions**:

**1. Normal Behavior**

```bash
# JVM caches tend to use ~90% of heap
# This is OK if:
# - No OOM errors
# - Eviction is working
# - GC pauses are < 50ms
```

**2. Trigger GC**

```bash
# Force GC (temporary relief)
jcmd <pid> GC.run

# Better: Reduce cache size
redis-cli -p 6380 FLUSHALL  # DANGER: Deletes all data
```

---

## Crash Debugging

### Chronos Won't Start

**Symptom**: Service fails to start

**Diagnosis**:

```bash
# Check logs
tail -50 /var/log/chronos/chronos.log

# Check service status
sudo systemctl status chronos
```

**Common Causes**:

**1. Port Already in Use**

```bash
# Evidence: "Address already in use" in logs
ERROR: Failed to bind to port 6380

# Solution: Find process using port
sudo lsof -i :6380
kill -9 <pid>

# Or: Change port
REDIS_PORT=6381
```

**2. Corrupt Snapshot**

```bash
# Evidence: "Failed to load snapshot" in logs
ERROR: SnapshotManager: Corrupt snapshot file

# Solution: Delete snapshot, start fresh
sudo rm /var/lib/chronos/snapshots/snapshot.rdb
sudo systemctl start chronos
```

**3. Missing Permissions**

```bash
# Evidence: "Permission denied" in logs
ERROR: Cannot write to /var/lib/chronos/snapshots

# Solution: Fix permissions
sudo chown -R chronos:chronos /var/lib/chronos
sudo chmod 755 /var/lib/chronos/snapshots
```

---

### Unexpected Crashes

**Symptom**: Chronos crashes randomly

**Diagnosis**:

```bash
# Check for segfault (JVM crash)
ls -la /tmp/hs_err_pid*.log

# Check OOM killer
dmesg | grep -i "out of memory"
```

**Solutions**:

**1. JVM Segfault**

```bash
# Read crash log
cat /tmp/hs_err_pid*.log

# Common causes:
# - Native library issue (Netty, compression)
# - JVM bug (update to latest JDK 17)

# Solution: Update JDK
sudo apt update && sudo apt install openjdk-17-jdk
```

**2. OOM Killer**

```bash
# Evidence: dmesg shows "Killed process <pid> (java)"

# Solution: Increase system RAM or reduce heap
-Xmx4g  # Reduce from 8g
```

---

## Network Issues

### Can't Connect to Chronos

**Symptom**: `redis-cli -p 6380` times out

**Diagnosis**:

```bash
# Check if Chronos is running
sudo systemctl status chronos

# Check if port is listening
sudo netstat -tlnp | grep 6380

# Check firewall
sudo ufw status
```

**Solutions**:

**1. Chronos Not Running**

```bash
sudo systemctl start chronos
```

**2. Firewall Blocking**

```bash
sudo ufw allow 6380/tcp
```

**3. Binding to Wrong Interface**

```bash
# Check bind address in logs
# Should show: Bound to 0.0.0.0:6380 (all interfaces)
# If shows: Bound to 127.0.0.1:6380 (localhost only)

# Solution: Configure bind address
-Dchronos.bind.address=0.0.0.0
```

---

### Connection Refused

**Symptom**: `Connection refused` error

**Diagnosis**:

```bash
telnet localhost 6380
# If fails: Chronos not listening

curl http://localhost:8080/health
# If fails: HTTP server not started
```

**Solutions**:

**1. Check Startup Logs**

```bash
tail -100 /var/log/chronos/chronos.log | grep -i error

# Look for:
# - "Failed to start server"
# - Exceptions during startup
```

---

## Data Issues

### Data Loss After Restart

**Symptom**: All keys gone after restart

**Diagnosis**:

```bash
# Check if snapshot was created
ls -lh /var/lib/chronos/snapshots/

# Check snapshot settings
env | grep SNAPSHOT
```

**Solutions**:

**1. Snapshot Disabled**

```bash
# Enable snapshots
SNAPSHOT_INTERVAL_SECONDS=300  # Every 5 minutes
sudo systemctl restart chronos
```

**2. Snapshot Failed to Write**

```bash
# Check permissions
ls -ld /var/lib/chronos/snapshots
# Should be writable by chronos user

# Fix:
sudo chown chronos:chronos /var/lib/chronos/snapshots
```

---

### Wrong Values Returned

**Symptom**: GET returns unexpected value

**Diagnosis**:

```bash
# Check if TTL expired
redis-cli -p 6380 TTL <key>

# Check if value was overwritten
# Review application logs for SET commands
```

**Solutions**:

**1. Stale Data**

```bash
# If using adaptive TTL, values may be cached longer
# Solution: Force refresh
redis-cli -p 6380 DEL <key>
# Next access will reload from backend
```

**2. Prefetch Loaded Wrong Data**

```bash
# Check prefetch logs
grep "Prefetched key" /var/log/chronos/chronos.log

# Disable prefetch if causing issues
curl -X POST http://localhost:8080/admin/prefetch/disable
```

---

## Monitoring Issues

### Prometheus Not Scraping

**Symptom**: No data in Grafana

**Diagnosis**:

```bash
# Test metrics endpoint
curl http://localhost:8080/metrics

# Check Prometheus targets
open http://localhost:9090/targets
# Should show chronos target as "UP"
```

**Solutions**:

**1. Wrong URL in prometheus.yml**

```yaml
# Fix:
scrape_configs:
  - job_name: "chronos"
    static_configs:
      - targets: ["chronos:8080"] # Use service name if Docker
      - targets: ["localhost:8080"] # Use localhost if same host
```

**2. Firewall Blocking Prometheus**

```bash
sudo ufw allow from <prometheus-ip> to any port 8080
```

---

### Grafana Shows No Data

**Symptom**: Grafana dashboard is empty

**Diagnosis**:

```bash
# Check if Prometheus has data
# Go to: http://localhost:9090/graph
# Query: chronos_hit_rate
# Should show graph
```

**Solutions**:

**1. Datasource Not Configured**

```bash
# In Grafana:
# Configuration → Data Sources → Add Prometheus
# URL: http://prometheus:9090 (if Docker)
# URL: http://localhost:9090 (if same host)
```

**2. Wrong Query in Dashboard**

```bash
# Check dashboard panel queries
# Should match metric names from /metrics endpoint
```

---

## Common Configuration Mistakes

### 1. Heap Size vs Cache Size

❌ **Wrong**:

```bash
-Xmx2g  # JVM heap
MAX_MEMORY_MB=8192  # Cache size
# Cache can't be larger than heap!
```

✅ **Correct**:

```bash
-Xmx8g  # JVM heap (larger)
MAX_MEMORY_MB=6144  # Cache size (70-80% of heap)
```

---

### 2. GC Tuning for Wrong Heap Size

❌ **Wrong**:

```bash
-Xmx16g -XX:MaxGCPauseMillis=10
# Impossible to achieve 10ms pauses with 16GB heap
```

✅ **Correct**:

```bash
-Xmx16g -XX:MaxGCPauseMillis=500
# Realistic target for large heap
```

---

### 3. Too Many Prefetch Threads

❌ **Wrong**:

```bash
PREFETCH_THREADS=100
# Wastes resources, doesn't help
```

✅ **Correct**:

```bash
PREFETCH_THREADS=4
# 1-2x CPU cores is optimal
```

---

## Getting Additional Help

### Collect Diagnostics

Before opening an issue, collect:

```bash
# 1. Version
curl http://localhost:8080/health | jq '.version'

# 2. Config
env | grep -E "CHRONOS|JAVA_OPTS"

# 3. Stats
curl http://localhost:8080/stats > stats.json

# 4. Logs (last 500 lines)
tail -500 /var/log/chronos/chronos.log > chronos.log

# 5. Thread dump
jstack <pid> > threaddump.txt

# 6. Heap histogram
jmap -histo <pid> > heap-histo.txt
```

### Open GitHub Issue

Include:

- [ ] Chronos version
- [ ] Java version (`java -version`)
- [ ] OS and version
- [ ] Configuration (env vars)
- [ ] stats.json
- [ ] Relevant logs
- [ ] Steps to reproduce

**Template**:

```markdown
## Environment

- Chronos version: 1.0.0
- Java: OpenJDK 17.0.9
- OS: Ubuntu 22.04

## Issue

[Describe the problem]

## Expected Behavior

[What should happen]

## Actual Behavior

[What actually happens]

## Steps to Reproduce

1. Start Chronos with config X
2. Run command Y
3. Observe error Z

## Logs

[Attach logs]

## Workaround

[Any temporary fix you found]
```

---

## Emergency Procedures

### Cache is Down, Need It Back ASAP

```bash
# 1. Try quick restart
sudo systemctl restart chronos

# 2. If fails, check logs
tail -50 /var/log/chronos/chronos.log

# 3. If corrupt snapshot, delete and restart
sudo systemctl stop chronos
sudo mv /var/lib/chronos/snapshots/snapshot.rdb /tmp/snapshot.rdb.bak
sudo systemctl start chronos

# 4. If still fails, restart with clean slate
sudo systemctl stop chronos
sudo rm -rf /var/lib/chronos/snapshots/*
sudo systemctl start chronos
```

### Runaway Memory Usage

```bash
# 1. Flush cache (DANGER: Loses all data)
redis-cli -p 6380 FLUSHALL

# 2. Restart with smaller heap
sudo systemctl stop chronos
# Edit systemd service: -Xmx2g (reduce from 8g)
sudo systemctl daemon-reload
sudo systemctl start chronos
```

### Data Corruption Suspected

```bash
# 1. Stop Chronos
sudo systemctl stop chronos

# 2. Backup everything
sudo cp -r /var/lib/chronos /backup/chronos-$(date +%Y%m%d)

# 3. Try restore from older snapshot
sudo cp /backup/chronos-20251230/snapshots/snapshot.rdb \
     /var/lib/chronos/snapshots/snapshot.rdb

# 4. Restart
sudo systemctl start chronos

# 5. Verify data
redis-cli -p 6380 GET <known-key>
```
