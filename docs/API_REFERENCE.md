# API Reference

Complete reference for Chronos Cache APIs: Redis Protocol and HTTP REST API.

---

## Redis Protocol API

Chronos implements a subset of Redis RESP2 protocol. Compatible with any Redis client library.

### Connection

**Default Port**: 6380

```bash
# Using redis-cli
redis-cli -p 6380

# Using programming language clients
# Python: redis-py
# Java: Jedis
# Node.js: node-redis
```

---

### Commands

#### GET

Retrieve value for a key.

**Syntax**: `GET key`

**Return**: Value if exists, `nil` if not found or expired

**Example**:

```bash
redis-cli -p 6380
> GET user:123
"Alice"

> GET nonexistent
(nil)
```

**Time Complexity**: O(1)

---

#### SET

Store a key-value pair.

**Syntax**: `SET key value [EX seconds] [PX milliseconds]`

**Options**:

- `EX seconds`: Set TTL in seconds
- `PX milliseconds`: Set TTL in milliseconds

**Return**: `+OK` on success

**Example**:

```bash
> SET user:123 "Alice"
OK

> SET session:abc "data" EX 300
OK

> SET temp:xyz "value" PX 5000
OK
```

**Time Complexity**: O(1)

**Validation**:

- Key max length: 1024 bytes
- Value max size: 10MB (configurable)
- TTL must be >= 0

---

#### DEL

Delete one or more keys.

**Syntax**: `DEL key [key ...]`

**Return**: Integer count of keys deleted

**Example**:

```bash
> DEL user:123
(integer) 1

> DEL key1 key2 key3
(integer) 3
```

**Time Complexity**: O(N) where N = number of keys

---

#### EXPIRE

Set TTL on an existing key.

**Syntax**: `EXPIRE key seconds`

**Return**:

- `(integer) 1` if TTL set
- `(integer) 0` if key doesn't exist

**Example**:

```bash
> SET user:123 "Alice"
OK

> EXPIRE user:123 300
(integer) 1
```

**Time Complexity**: O(1)

---

#### TTL

Get remaining TTL for a key.

**Syntax**: `TTL key`

**Return**:

- Positive integer: Seconds until expiration
- `-1`: Key exists but no TTL
- `-2`: Key doesn't exist

**Example**:

```bash
> SET session:abc "data" EX 300
OK

> TTL session:abc
(integer) 297

> TTL nonexistent
(integer) -2
```

**Time Complexity**: O(1)

---

#### KEYS

Find all keys matching a pattern.

**Syntax**: `KEYS pattern`

**Pattern**:

- `*`: Match any characters
- `?`: Match one character
- `[abc]`: Match one of a, b, or c

**Return**: Array of matching keys

**Example**:

```bash
> KEYS user:*
1) "user:123"
2) "user:456"

> KEYS session:???
1) "session:abc"
2) "session:xyz"
```

> [!WARNING]
> KEYS scans entire cache. Use sparingly in production.

**Time Complexity**: O(N) where N = total keys

---

#### FLUSHALL

Delete all keys from cache.

**Syntax**: `FLUSHALL`

**Return**: `+OK`

**Example**:

```bash
> FLUSHALL
OK
```

> [!CAUTION]
> Irreversible operation. Use with extreme caution.

**Time Complexity**: O(N) where N = total keys

---

#### INFO

Get statistics about the cache.

**Syntax**: `INFO [section]`

**Sections**:

- `server`: Server info
- `stats`: Statistics
- `memory`: Memory usage
- (no section): All info

**Return**: Multi-line string with key-value pairs

**Example**:

```bash
> INFO stats
# Stats
total_keys:15234
hits:128945
misses:23891
hit_rate:0.844
evictions:892
```

**Time Complexity**: O(1)

---

#### PING

Test connection.

**Syntax**: `PING [message]`

**Return**:

- `PONG` if no message
- Echo message if provided

**Example**:

```bash
> PING
PONG

> PING "Hello"
"Hello"
```

**Time Complexity**: O(1)

---

## HTTP REST API

JSON-based API for monitoring and administration.

**Base URL**: `http://localhost:8080`

**Content-Type**: `application/json`

---

### GET /health

Health check endpoint with diagnostics.

**Response**:

```json
{
  "score": 95,
  "status": "healthy",
  "hitRate": 0.87,
  "p99Latency": 1.2,
  "memoryUsage": 0.45,
  "issues": []
}
```

**Fields**:

- `score` (integer 0-100): Overall health score
- `status` (string): `healthy` | `degraded` | `unhealthy`
- `hitRate` (float): Cache hit rate (0.0-1.0)
- `p99Latency` (float): P99 latency in milliseconds
- `memoryUsage` (float): Memory usage ratio (0.0-1.0)
- `issues` (array): List of detected issues

**Status Codes**:

- `200 OK`: Cache is operational
- `503 Service Unavailable`: Critical issues detected

**Example**:

```bash
curl http://localhost:8080/health

# Check if healthy (exit code 0 if healthy)
curl -f http://localhost:8080/health || echo "Unhealthy!"
```

---

### GET /metrics

Prometheus-format metrics.

**Response**:

```
# HELP chronos_hits_total Total cache hits
# TYPE chronos_hits_total counter
chronos_hits_total 15234

# HELP chronos_misses_total Total cache misses
# TYPE chronos_misses_total counter
chronos_misses_total 2891

# HELP chronos_hit_rate Current hit rate
# TYPE chronos_hit_rate gauge
chronos_hit_rate 0.8405

# HELP chronos_latency_p99_ms P99 latency in milliseconds
# TYPE chronos_latency_p99_ms gauge
chronos_latency_p99_ms 1.2

# HELP chronos_evictions_total Total evictions
# TYPE chronos_evictions_total counter
chronos_evictions_total 892

# HELP chronos_prefetch_accuracy Prefetch accuracy ratio
# TYPE chronos_prefetch_accuracy gauge
chronos_prefetch_accuracy 0.847
```

**Status Codes**:

- `200 OK`: Metrics available

**Example**:

```bash
curl http://localhost:8080/metrics

# Parse with Prometheus
curl http://localhost:8080/metrics | grep hit_rate
```

---

### GET /stats

Detailed JSON statistics.

**Response**:

```json
{
  "timestamp": "2025-12-31T22:30:00Z",
  "uptime_seconds": 86400,
  "cache": {
    "total_keys": 15234,
    "total_hits": 128945,
    "total_misses": 23891,
    "hit_rate": 0.844,
    "evictions": 892,
    "memory_bytes": 2147483648,
    "memory_usage_ratio": 0.45
  },
  "performance": {
    "throughput_ops_per_sec": 1250,
    "latency_p50_ms": 0.65,
    "latency_p95_ms": 1.2,
    "latency_p99_ms": 1.8,
    "latency_p999_ms": 3.2
  },
  "prefetch": {
    "predictions_made": 5832,
    "predictions_hit": 4956,
    "accuracy": 0.847
  }
}
```

**Status Codes**:

- `200 OK`: Stats available

**Example**:

```bash
curl http://localhost:8080/stats | jq '.cache.hit_rate'
```

---

### POST /admin/snapshot

Trigger manual snapshot.

**Request**: No body required

**Response**:

```json
{
  "success": true,
  "snapshot_path": "/var/lib/chronos/snapshots/snapshot-20251231-223000.rdb",
  "size_bytes": 524288000,
  "duration_ms": 450
}
```

**Status Codes**:

- `200 OK`: Snapshot created successfully
- `500 Internal Server Error`: Snapshot failed

**Example**:

```bash
curl -X POST http://localhost:8080/admin/snapshot
```

> [!WARNING]
> Snapshot operation briefly blocks writes. Use during low-traffic periods if possible.

---

### POST /admin/prefetch/enable

Enable predictive prefetching.

**Response**:

```json
{
  "success": true,
  "prefetching_enabled": true
}
```

**Example**:

```bash
curl -X POST http://localhost:8080/admin/prefetch/enable
```

---

### POST /admin/prefetch/disable

Disable predictive prefetching.

**Response**:

```json
{
  "success": true,
  "prefetching_enabled": false
}
```

**Example**:

```bash
curl -X POST http://localhost:8080/admin/prefetch/disable
```

---

## Error Codes

### Redis Protocol Errors

Redis errors are returned as `-ERR message\r\n`

| Error                            | Meaning                      |
| -------------------------------- | ---------------------------- |
| `-ERR unknown command`           | Command not supported        |
| `-ERR wrong number of arguments` | Invalid command syntax       |
| `-ERR value is not an integer`   | Expected integer, got string |
| `-ERR key too long`              | Key exceeds 1024 bytes       |
| `-ERR value too large`           | Value exceeds 10MB           |

**Example**:

```bash
> GET
-ERR wrong number of arguments for 'get' command
```

### HTTP Error Codes

| Code                        | Meaning                   |
| --------------------------- | ------------------------- |
| `200 OK`                    | Request successful        |
| `400 Bad Request`           | Invalid request format    |
| `404 Not Found`             | Endpoint doesn't exist    |
| `500 Internal Server Error` | Server error (check logs) |
| `503 Service Unavailable`   | Cache is unhealthy        |

---

## Client Libraries

Chronos is compatible with standard Redis clients:

### Java (Jedis)

```java
Jedis client = new Jedis("localhost", 6380);
client.set("key", "value");
String value = client.get("key");
client.close();
```

### Python (redis-py)

```python
import redis

client = redis.Redis(host='localhost', port=6380)
client.set('key', 'value')
value = client.get('key')
```

### Node.js (node-redis)

```javascript
const redis = require("redis");
const client = redis.createClient({ port: 6380 });

await client.set("key", "value");
const value = await client.get("key");
```

### Go (go-redis)

```go
import "github.com/go-redis/redis/v8"

client := redis.NewClient(&redis.Options{
    Addr: "localhost:6380",
})

client.Set(ctx, "key", "value", 0)
val, _ := client.Get(ctx, "key").Result()
```

---

## Rate Limits

Currently, Chronos does not enforce rate limits. For production, use:

- Network firewall rules
- Reverse proxy (nginx) with rate limiting
- Application-level throttling

---

## Versioning

API follows semantic versioning:

- **Major**: Breaking changes
- **Minor**: New features (backward compatible)
- **Patch**: Bug fixes

Current version: `1.0.0`

Check version:

```bash
curl http://localhost:8080/health | jq '.version'
```
