# Deployment Guide

> [!IMPORTANT]
> This guide is for deploying Chronos to production or production-like environments. For local development, see the Quick Start in the README.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Deployment Options](#deployment-options)
3. [Configuration](#configuration)
4. [Security Hardening](#security-hardening)
5. [Backup & Restore](#backup--restore)
6. [Monitoring Setup](#monitoring-setup)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### System Requirements

**Minimum** (dev/test):

- CPU: 2 cores
- RAM: 2GB
- Disk: 5GB (for snapshots)
- Java: OpenJDK 17+

**Recommended** (production):

- CPU: 4-8 cores
- RAM: 8-16GB
- Disk: 50GB SSD (for snapshots + logs)
- Java: OpenJDK 17.0.9+ (LTS)

### Software Dependencies

- Docker 20.10+ (if using containers)
- Docker Compose 2.0+ (for stack deployment)
- Redis CLI tools (for testing)
- Optional: Prometheus, Grafana (for monitoring)

---

## Deployment Options

### Option 1: Docker Compose (Recommended)

**Best for**: Single-node deployments, quick setup

```bash
# 1. Clone repository
git clone https://github.com/krishnamouli8/Chronos.git
cd Chronos

# 2. Build JAR
mvn clean package -DskipTests

# 3. Configure environment (see Configuration section)
cp .env.example .env
vi .env

# 4. Start stack
docker-compose up -d

# 5. Verify
redis-cli -p 6380 PING  # Should return PONG
curl http://localhost:8080/health
```

**Services Started**:

- Chronos Cache (port 6380 Redis, 8080 HTTP)
- Prometheus (port 9090)
- Grafana (port 3000)

---

### Option 2: Standalone JAR

**Best for**: Custom environments, no Docker

```bash
# 1. Build
mvn clean package

# 2. Run
java -jar target/Chronos-1.0-SNAPSHOT.jar \
  -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200
```

**Environment Variables** (see Configuration section)

---

### Option 3: systemd Service

**Best for**: Linux servers, auto-restart

```bash
# 1. Create service file
sudo tee /etc/systemd/system/chronos.service > /dev/null <<EOF
[Unit]
Description=Chronos Cache Service
After=network.target

[Service]
Type=simple
User=chronos
WorkingDirectory=/opt/chronos
ExecStart=/usr/bin/java \\
  -Xmx4g \\
  -XX:+UseG1GC \\
  -XX:MaxGCPauseMillis=200 \\
  -jar /opt/chronos/Chronos-1.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10s

[Install]
WantedBy=multi-user.target
EOF

# 2. Enable and start
sudo systemctl daemon-reload
sudo systemctl enable chronos
sudo systemctl start chronos

# 3. Check status
sudo systemctl status chronos
```

---

## Configuration

### Environment Variables

Create `.env` file (or export variables):

```bash
# JVM Settings
JAVA_OPTS="-Xmx4g -Xms4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled"

# Cache Settings
MAX_MEMORY_MB=4096
NUM_SEGMENTS=256
EVICTION_POLICY=LRU

# Network Settings
REDIS_PORT=6380
HTTP_PORT=8080

# Prefetch Settings
PREFETCH_ENABLED=true
PREFETCH_CONFIDENCE=0.6
PREFETCH_THREADS=4

# Persistence
SNAPSHOT_INTERVAL_SECONDS=300
SNAPSHOT_PATH=/var/lib/chronos/snapshots/snapshot.rdb
SNAPSHOT_RETENTION_COUNT=10

# Health Monitoring
HEALTH_CHECK_INTERVAL_SECONDS=30

# Logging
LOG_LEVEL=INFO
LOG_PATH=/var/log/chronos/chronos.log
```

### JVM Tuning for Production

**For Caches < 2GB**:

```bash
-Xmx2g -Xms2g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:G1HeapRegionSize=16m
```

**For Caches 2-8GB**:

```bash
-Xmx8g -Xms8g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:G1HeapRegionSize=32m \
-XX:+ParallelRefProcEnabled \
-XX:+UseStringDeduplication
```

**For Caches > 8GB**:

```bash
-Xmx16g -Xms16g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=500 \
-XX:G1HeapRegionSize=64m \
-XX:+ParallelRefProcEnabled \
-XX:+UseStringDeduplication \
-XX:InitiatingHeapOccupancyPercent=35
```

**GC Logging** (for troubleshooting):

```bash
-Xlog:gc*:file=/var/log/chronos/gc.log:time,uptime,level,tags
```

---

## Security Hardening

> [!WARNING]
> Chronos currently does not include built-in authentication or encryption. Follow these guidelines to secure your deployment.

### Network Security

**1. Firewall Rules**

Only allow access from trusted sources:

```bash
# Allow only internal network
sudo ufw allow from 10.0.0.0/8 to any port 6380
sudo ufw allow from 10.0.0.0/8 to any port 8080

# Deny public access
sudo ufw deny 6380
sudo ufw deny 8080
```

**2. Bind to Internal Interface**

Modify startup to bind only to internal IP:

```bash
-Dchronos.bind.address=10.0.1.50  # Internal IP only
```

### Application Security

**1. Run as Non-Root User**

```bash
# Create dedicated user
sudo useradd -r -s /bin/false chronos
sudo mkdir -p /opt/chronos /var/lib/chronos /var/log/chronos
sudo chown -R chronos:chronos /opt/chronos /var/lib/chronos /var/log/chronos

# Run as chronos user (see systemd example)
```

**2. File Permissions**

```bash
# Restrict snapshot directory
chmod 700 /var/lib/chronos/snapshots

# Restrict logs
chmod 640 /var/log/chronos/*.log
```

**3. Disable Dangerous Commands** (future feature)

In production, disable:

- `FLUSHALL` - catastrophic data loss
- `KEYS *` - can block cache

### Recommended Network Architecture

```
┌─────────────┐
│   Internet  │
└──────┬──────┘
       │
┌──────▼──────┐
│   Firewall  │  ← Only allows VPN or internal
└──────┬──────┘
       │
┌──────▼──────┐
│   App Tier  │  ← Your application servers
└──────┬──────┘
       │
┌──────▼──────┐
│   Chronos   │  ← Not publicly exposed
└─────────────┘
```

---

## Backup & Restore

### Automated Snapshots

Chronos automatically creates snapshots based on `SNAPSHOT_INTERVAL_SECONDS`.

**Configuration**:

```bash
SNAPSHOT_INTERVAL_SECONDS=300  # Every 5 minutes
SNAPSHOT_PATH=/var/lib/chronos/snapshots/snapshot.rdb
SNAPSHOT_RETENTION_COUNT=10  # Keep last 10 snapshots
```

**Snapshot Rotation**:

```bash
# Snapshots are named: snapshot-<timestamp>.rdb
/var/lib/chronos/snapshots/
├── snapshot-20251231-120000.rdb
├── snapshot-20251231-120500.rdb
└── snapshot-20251231-121000.rdb (latest)
```

### Manual Backup

```bash
# 1. Trigger snapshot via HTTP API
curl -X POST http://localhost:8080/admin/snapshot

# 2. Copy snapshot file
sudo cp /var/lib/chronos/snapshots/snapshot-latest.rdb \
     /backup/chronos/snapshot-$(date +%Y%m%d).rdb

# 3. Upload to S3 (optional)
aws s3 cp /backup/chronos/snapshot-$(date +%Y%m%d).rdb \
     s3://my-bucket/chronos-backups/
```

### Restore Procedure

**From Snapshot**:

```bash
# 1. Stop Chronos
sudo systemctl stop chronos

# 2. Replace snapshot file
sudo cp /backup/chronos/snapshot-20251231.rdb \
     /var/lib/chronos/snapshots/snapshot.rdb

# 3. Start Chronos
sudo systemctl start chronos

# 4. Verify
redis-cli -p 6380 INFO | grep keys
```

**Cold Start** (no snapshot):

- Chronos starts empty
- Prefetcher learns patterns over time (~1 hour for 85% accuracy)

---

## Monitoring Setup

### Prometheus Configuration

**1. Configure Scraping**

Edit `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: "chronos"
    static_configs:
      - targets: ["localhost:8080"]
    metrics_path: "/metrics"
    scrape_interval: 15s
```

**2. Add Alerting Rules**

Create `alerts.yml`:

```yaml
groups:
  - name: chronos_alerts
    rules:
      - alert: LowCacheHitRate
        expr: chronos_hit_rate < 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Chronos cache hit rate below 50%"

      - alert: HighLatency
        expr: chronos_latency_p99_ms > 10
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Chronos P99 latency above 10ms"

      - alert: HighEvictionRate
        expr: rate(chronos_evictions_total[1m]) > 100
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Chronos evicting > 100 entries/sec"
```

**3. Restart Prometheus**

```bash
docker-compose restart prometheus
```

### Grafana Dashboard

**1. Import Dashboard**

- Open Grafana: http://localhost:3000
- Login: admin/admin
- Import → Upload `grafana-dashboard.json` (from repo)

**2. Key Panels**:

- Cache hit rate (gauge)
- Throughput (graph)
- Latency (heatmap)
- Memory usage (gauge)
- Prefetch accuracy (gauge)

### Health Checks

**HTTP Endpoint**:

```bash
curl http://localhost:8080/health

# Response:
{
  "score": 95,
  "status": "healthy",
  "hitRate": 0.87,
  "p99Latency": 1.2,
  "issues": []
}
```

**Load Balancer Health Check**:

```bash
# Configure LB to probe:
GET /health
Expect: HTTP 200
Interval: 10s
Timeout: 5s
```

---

## Production Checklist

Before going to production, ensure:

- [ ] JVM heap sized appropriately (50-70% of system RAM)
- [ ] G1GC tuning configured
- [ ] Snapshots enabled and tested restore procedure
- [ ] Firewall rules configured (no public access)
- [ ] Running as non-root user
- [ ] Logging configured and rotated
- [ ] Prometheus scraping configured
- [ ] Grafana alerts set up
- [ ] Health check endpoint tested
- [ ] Backup procedure documented and automated
- [ ] Disaster recovery plan written
- [ ] Load testing completed (see PERFORMANCE.md)

---

## Disaster Recovery

### Scenario 1: Cache Crash

**Detection**: Health check fails, service down

**Recovery**:

```bash
# 1. Check logs
tail -100 /var/log/chronos/chronos.log

# 2. Restart service
sudo systemctl restart chronos

# 3. Verify restore from snapshot
redis-cli -p 6380 INFO | grep keys

# 4. If corrupt snapshot, start fresh
sudo rm /var/lib/chronos/snapshots/snapshot.rdb
sudo systemctl restart chronos
```

**Downtime**: 10-30 seconds

### Scenario 2: Data Corruption

**Detection**: Incorrect values, crashes on access

**Recovery**:

```bash
# 1. Stop Chronos
sudo systemctl stop chronos

# 2. Restore from known-good backup
sudo cp /backup/chronos/snapshot-good.rdb \
     /var/lib/chronos/snapshots/snapshot.rdb

# 3. Restart
sudo systemctl start chronos
```

**Downtime**: Data loss = time since good backup

### Scenario 3: Disk Full

**Detection**: Snapshot failures, logs warn "No space"

**Recovery**:

```bash
# 1. Clean old snapshots
sudo find /var/lib/chronos/snapshots -type f -mtime +7 -delete

# 2. Rotate logs
sudo logrotate /etc/logrotate.d/chronos

# 3. Expand disk (if cloud)
# AWS: resize EBS, extend filesystem
```

---

## Scaling Guidelines

### Vertical Scaling

**When hit rate is good but latency is high**:

- Increase CPU cores (more parallelism)
- Increase RAM (larger cache)

**Resource Sizing**:
| Cache Entries | Avg Value | RAM Needed | CPU Cores |
|---------------|-----------|-----------|-----------|
| 100K | 1KB | 1GB | 2 |
| 1M | 1KB | 4GB | 4 |
| 10M | 1KB | 16GB | 8 |

### Horizontal Scaling (Future)

Currently, Chronos is single-node. For multi-node:

- **Client-side sharding**: App routes keys to different Chronos instances
- **Consistent hashing**: Use library like Jedis ShardedPool

**Future Roadmap**: Built-in clustering support

---

## Upgrade Procedure

```bash
# 1. Trigger snapshot
curl -X POST http://localhost:8080/admin/snapshot

# 2. Stop Chronos
sudo systemctl stop chronos

# 3. Backup current JAR
sudo cp /opt/chronos/Chronos-1.0-SNAPSHOT.jar \
     /opt/chronos/Chronos-1.0-SNAPSHOT.jar.bak

# 4. Deploy new JAR
sudo cp target/Chronos-1.1-SNAPSHOT.jar /opt/chronos/

# 5. Update systemd (if JAR name changed)
sudo vi /etc/systemd/system/chronos.service
sudo systemctl daemon-reload

# 6. Start new version
sudo systemctl start chronos

# 7. Verify
redis-cli -p 6380 INFO
curl http://localhost:8080/health

# 8. Rollback if issues
sudo systemctl stop chronos
sudo cp /opt/chronos/Chronos-1.0-SNAPSHOT.jar.bak \
     /opt/chronos/Chronos-1.0-SNAPSHOT.jar
sudo systemctl start chronos
```

---

## Support

For deployment issues:

- Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- Review logs: `/var/log/chronos/chronos.log`
- Open GitHub issue with:
  - Chronos version (`/health` endpoint shows build info)
  - Java version (`java -version`)
  - Relevant logs
  - Steps to reproduce
