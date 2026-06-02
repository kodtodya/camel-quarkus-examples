# Insurance Policy Camel POC

> **Quarkus 3.17.7 + Apache Camel 4.x** — Bulk processing of 200,000 insurance policy records with best-practice patterns: pagination, stream caching, parallel virtual threads, SEDA async queue, Micrometer metrics, H2 browser console, Prometheus, and Swagger UI.

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Quick Start — Local (Maven)](#quick-start--local-maven)
5. [Quick Start — Docker](#quick-start--docker)
6. [H2 Database Browser Access](#h2-database-browser-access)
7. [REST API Reference](#rest-api-reference)
8. [Observability](#observability)
9. [Configuration Reference](#configuration-reference)
10. [Best Practices Applied](#best-practices-applied)
11. [Tuning Guide](#tuning-guide)
12. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│  HTTP Client                                                           │
│  POST /api/v1/policies/process/start                                  │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────┐
│   PolicyProcessingResource (JAX-RS)      │
│   - Creates ProcessingBatch              │
│   - Calls direct:startBulkProcessing     │
│   - Returns 202 Accepted immediately     │
└──────────────────────────┬───────────────┘
                           │ async
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│  ROUTE: bulkProcessingOrchestrator (direct:startBulkProcessing)      │
│                                                                      │
│  LOOP while page not empty                                           │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  PolicyPageFetchProcessor                                    │    │
│  │  ┌───────────────────────────────────────────────────────┐  │    │
│  │  │  SELECT * FROM insurance_policy                        │  │    │
│  │  │  WHERE processed = FALSE                               │  │    │
│  │  │  ORDER BY id                                           │  │    │
│  │  │  LIMIT 1000 OFFSET (pageNum * 1000)                   │  │    │
│  │  │  [Forward-only cursor, stream mode, readOnly conn]     │  │    │
│  │  └───────────────────────────────────────────────────────┘  │    │
│  └──────────────────────────┬────────────────────────────────────┘    │
│                             │ List<InsurancePolicy> (1000 records)    │
│                             ▼                                         │
│              seda:processPage (async hand-off)                        │
│              Queue size: 50,000  ▲  backpressure                      │
└─────────────────────────────────────────────────────────────────────-─┘
                             │
           ┌─────────────────┴──────────────┐
           │  ROUTE: sedaPageConsumer        │
           │  concurrentConsumers = 10       │
           │  (10 threads consume the queue) │
           └────────────────┬───────────────┘
                            │
                            ▼
           ┌────────────────────────────────────────────┐
           │  PolicyBatchProcessor                       │
           │  ┌──────────────────────────────────────┐  │
           │  │ Split page → sub-batches of 100      │  │
           │  │ Process each sub-batch via            │  │
           │  │ CompletableFuture + Virtual Threads   │  │
           │  │ (Java 21)                             │  │
           │  └────────────────┬─────────────────────┘  │
           │                   │                         │
           │  PolicyProcessingService                    │
           │  - validate()                               │
           │  - enrich()    (risk, batch tagging)        │
           │  - applyBusinessRules()                     │
           │     ↓                                       │
           │  Bulk UPDATE (JDBC batch, 500/commit)       │
           │  UPDATE insurance_policy SET processed=TRUE │
           └────────────────────────────────────────────┘
                            │
                            ▼
           ┌────────────────────────────────────────────┐
           │  Micrometer / Prometheus metrics            │
           │  JMX / Camel management                    │
           └────────────────────────────────────────────┘
```

---

## Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Quarkus | 3.17.7 | Runtime framework |
| Apache Camel Quarkus | 3.17.0 | Integration/routing engine |
| H2 Database | 2.x (bundled) | Embedded SQL (TCP server mode) |
| Flyway | Bundled with Quarkus | Schema migration + data seeding |
| Micrometer | Bundled | Metrics instrumentation |
| Prometheus Registry | Bundled | Metrics export |
| SmallRye Health | Bundled | Liveness / Readiness probes |
| SmallRye OpenAPI | Bundled | Swagger UI |
| Java | 21 | Virtual threads (Loom) |
| Maven | 3.9+ | Build tool |

---

## Project Structure

```
insurance-camel-poc/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/insurance/poc/
    │   │   ├── config/
    │   │   │   ├── H2ServerConfig.java          # Starts H2 TCP + Web servers
    │   │   │   ├── CamelThreadPoolConfig.java   # Registers thread pool profiles
    │   │   │   └── PolicyProcessingConfig.java  # Type-safe config mapping
    │   │   ├── model/
    │   │   │   ├── InsurancePolicy.java         # Domain model
    │   │   │   └── ProcessingBatch.java         # Batch tracking model
    │   │   ├── route/
    │   │   │   └── InsurancePolicyRoute.java    # ★ Main Camel route
    │   │   ├── processor/
    │   │   │   ├── PolicyPageFetchProcessor.java # Paginated DB reader
    │   │   │   └── PolicyBatchProcessor.java    # Parallel page processor
    │   │   ├── service/
    │   │   │   ├── PolicyProcessingService.java # Business logic
    │   │   │   ├── BatchStateService.java       # Batch lifecycle
    │   │   │   └── PolicyRowMapper.java         # ResultSet → domain
    │   │   ├── metrics/
    │   │   │   └── ProcessingMetrics.java       # Custom Micrometer metrics
    │   │   └── resource/
    │   │       ├── PolicyProcessingResource.java # REST API
    │   │       └── InsuranceHealthCheck.java    # Health probes
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           ├── V1__Create_Insurance_Policy_Schema.sql
    │           └── V2__Seed_Insurance_Policy_Data.sql
    └── test/
        ├── java/com/insurance/poc/
        │   └── PolicyProcessingResourceTest.java
        └── resources/
            └── application.properties
```

---

## Quick Start — Local (Maven)

### Prerequisites
- Java 21+
- Maven 3.9+

### Steps

```bash
# 1. Clone / enter project
cd insurance-camel-quarkus-example

# 2. Run in dev mode in new terminal (live reload, auto-starts H2, runs Flyway migrations)
./mvnw quarkus:dev

# Or standard run
./mvnw package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

### First-time startup
Flyway will automatically:
1. Create the `insurance_policy` table and indexes (V1)
2. Insert **200,000** policy records using `SYSTEM_RANGE(1, 200000)` (V2)

> ⚠️ The first startup may take **60–120 seconds** while inserting 200K rows. Watch the logs:
> ```
> INFO  [org.fly.cor.int.com.DbMigrate] Successfully applied 2 migrations
> ```

### Trigger processing

```bash
curl -X POST "http://localhost:8080/api/v1/policies/process/start?triggeredBy=MyTest"
```

### Poll status

```bash
curl http://localhost:8080/api/v1/policies/process/status/BATCH-XXXXXXXX
```

---

## Quick Start — Docker

### Build and run

```bash
# Build image
docker build -t insurance-camel-poc:1.0.0 .

# Run container
docker run -d \
  --name insurance-camel-poc \
  -p 8080:8080 \
  -p 8082:8082 \
  -p 9092:9092 \
  insurance-camel-poc:1.0.0

# Or using Docker Compose
docker-compose up -d

# Follow logs
docker-compose logs -f
```

### Wait for readiness (≈90s for Flyway seeding)

```bash
# Watch health
watch curl -s http://localhost:8080/q/health/ready
```

---

## H2 Database Browser Access

### Option 1: Quarkus H2 Console (recommended)

**URL:** http://localhost:8080/h2-console

| Field | Value |
|-------|-------|
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:mem:testinsurancedb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` |
| Username | `sa` |
| Password | `sa` |

### Option 2: Standalone H2 Web Console

**URL:** http://localhost:8082

Same connection details as above.

### Useful queries

```sql
-- Total record count
SELECT COUNT(*) FROM insurance_policy;

-- By status
SELECT status, COUNT(*) AS cnt FROM insurance_policy GROUP BY status;

-- By type
SELECT policy_type, COUNT(*) AS cnt FROM insurance_policy GROUP BY policy_type;

-- Unprocessed
SELECT COUNT(*) FROM insurance_policy WHERE processed = FALSE;

-- Processing batches
SELECT * FROM processing_batch ORDER BY start_time DESC;

-- High value policies
SELECT policy_number, holder_first_name, holder_last_name, insured_amount
FROM insurance_policy
WHERE insured_amount > 9000000
ORDER BY insured_amount DESC
LIMIT 10;

-- Reset all records to unprocessed (for re-running)
UPDATE insurance_policy SET processed = FALSE, processed_at = NULL,
       processing_batch_id = NULL WHERE processed = TRUE;
```

---

## REST API Reference

Base URL: `http://localhost:8080`

Swagger UI: http://localhost:8080/swagger-ui

### Endpoints

#### `POST /api/v1/policies/process/start`
Trigger a new bulk processing batch.

**Query parameters:**
- `triggeredBy` (optional, default: `REST_API`) — identifier for audit

**Response 202:**
```json
{
  "batchId": "BATCH-A1B2C3D4",
  "status": "RUNNING",
  "startTime": "2025-01-15T10:30:00",
  "triggeredBy": "MyTest",
  "message": "Processing started. Poll /api/v1/policies/process/status/BATCH-A1B2C3D4"
}
```

**Response 409** — another batch is running.

---

#### `GET /api/v1/policies/process/status/{batchId}`
Get status of a specific batch.

```json
{
  "batchId": "BATCH-A1B2C3D4",
  "status": "COMPLETED",
  "startTime": "2025-01-15T10:30:00",
  "endTime": "2025-01-15T10:35:42",
  "totalRecords": 200000,
  "processedRecords": 199850,
  "failedRecords": 150,
  "durationMillis": 342000,
  "throughputPerSec": "584.7"
}
```

---

#### `GET /api/v1/policies/process/status`
List all batches (current session).

---

#### `GET /api/v1/policies/process/active`
Check if processing is currently running.

```json
{ "active": true }
```

---

#### `GET /api/v1/policies/metrics/summary`
Custom metrics summary.

```json
{
  "totalProcessed": 200000,
  "totalFailed": 0,
  "batchCurrentlyActive": false,
  "prometheusEndpoint": "/q/metrics"
}
```

---

#### `GET /api/v1/policies/info`
Application info + all endpoint links.

---

## Observability

### Health Checks
| Endpoint | Description |
|----------|-------------|
| `GET /q/health` | Combined health |
| `GET /q/health/live` | Liveness (app running) |
| `GET /q/health/ready` | Readiness (DB connected) |

### Prometheus Metrics
**Endpoint:** `GET /q/metrics`

Key custom metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `insurance_policies_processed_total` | Counter | Records successfully processed |
| `insurance_policies_failed_total` | Counter | Records that failed processing |
| `insurance_batch_active` | Gauge | 1=batch running, 0=idle |
| `insurance_page_processing_duration_*` | Timer | Per-page processing time (p50, p90, p99) |
| `insurance_throughput_records_per_second` | Gauge | Records/sec (last page) |

Camel built-in metrics:
- `camel_exchanges_total`
- `camel_route_policy_*`
- `camel_context_*`

### JVM metrics (auto-exported)
- `jvm_memory_*`
- `jvm_threads_*`
- `jvm_gc_*`
- `process_cpu_*`

---

## Configuration Reference

All properties in `src/main/resources/application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `insurance.processing.page-size` | `1000` | Records per DB page |
| `insurance.processing.seda-concurrency` | `10` | SEDA consumer threads |
| `insurance.processing.seda-queue-size` | `50000` | Max SEDA queue depth |
| `insurance.processing.thread-pool-size` | `20` | Core thread pool |
| `insurance.processing.thread-pool-max-size` | `40` | Max thread pool |
| `insurance.processing.thread-pool-queue-size` | `10000` | Thread pool queue |
| `insurance.processing.batch-log-interval` | `5000` | Progress log frequency |
| `quarkus.datasource.jdbc.max-size` | `50` | Connection pool max |
| `quarkus.h2.console.enabled` | `true` | Enable H2 web console |

Override at runtime:
```bash
java -jar target/quarkus-app/quarkus-run.jar \
  -Dinsurance.processing.page-size=2000 \
  -Dinsurance.processing.seda-concurrency=20
```

Or via environment variables:
```bash
INSURANCE_PROCESSING_PAGE_SIZE=2000 \
INSURANCE_PROCESSING_SEDA_CONCURRENCY=20 \
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Best Practices Applied

### 1. Pagination (LIMIT / OFFSET)
Instead of loading 200K records into memory at once, the `PolicyPageFetchProcessor` fetches `page-size` rows per iteration using `LIMIT ? OFFSET ?`. This keeps heap usage bounded.

### 2. Stream Caching (Spool to Disk)
```
camel.context.stream-caching-enabled=true
camel.context.stream-caching-spool-enabled=true
camel.context.stream-caching-spool-threshold=65536  # bytes
```
Large message bodies that exceed the threshold are spooled to disk (`/tmp/camel-spool`) instead of held in heap, preventing OOM errors.

### 3. SEDA Async Queue (Decoupling)
The DB pagination loop sends pages to `seda:processPage` without waiting (`waitForTaskToComplete=Never`). This decouples the DB fetch thread from the processing threads, allowing both to run at full speed with backpressure via `blockWhenFull=true`.

### 4. Parallel Virtual Threads (Java 21)
`PolicyBatchProcessor` uses `Executors.newVirtualThreadPerTaskExecutor()` to process sub-batches of 100 records in parallel with minimal overhead. Virtual threads (Project Loom) are ideal for IO-bound work.

### 5. Multi-Threading via SEDA concurrentConsumers
```java
from("seda:processPage?concurrentConsumers=10&size=50000")
```
10 threads simultaneously consume from the SEDA queue, multiplying throughput.

### 6. Bulk JDBC Updates
Rather than `UPDATE` per record (200K round-trips), `PolicyBatchProcessor` batches 500 updates per `executeBatch()` call, dramatically reducing DB round-trips.

### 7. Idempotent Processing
The SQL query always filters `WHERE processed = FALSE`, so re-running is safe — already-processed records are skipped automatically.

### 8. Dead Letter Channel
Failed exchanges are routed to `direct:deadLetter` after 3 exponential-backoff retries, logged for audit, preventing silent failures.

### 9. Read-Only / Forward-Only Cursor
```java
conn.setReadOnly(true);
ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
ps.setFetchSize(500);
```
Hints the JDBC driver to stream rows instead of buffering all in memory.

### 10. Connection Pool Tuning
Agroal pool sized to 10–50 connections with health checks and proper timeouts to handle concurrent page fetches and bulk updates without exhausting the pool.

---

## Tuning Guide

### For faster throughput
```properties
insurance.processing.page-size=2000
insurance.processing.seda-concurrency=20
insurance.processing.thread-pool-size=40
insurance.processing.thread-pool-max-size=80
quarkus.datasource.jdbc.max-size=80
```

### For lower memory usage
```properties
insurance.processing.page-size=500
insurance.processing.seda-queue-size=10000
camel.context.stream-caching-spool-threshold=32768
```

### JVM heap
```bash
# For 200K records, 1–2GB heap is comfortable
java -Xms512m -Xmx2g -XX:+UseG1GC -jar quarkus-run.jar
```

---

## Troubleshooting

### Startup takes > 2 minutes
Normal on first run — Flyway inserts 200K rows. Watch logs for:
```
INFO Flyway: Successfully applied 2 migrations
```

### H2 Console can't connect
Ensure you use TCP URL, not file URL:
- ✅ `jdbc:h2:tcp://localhost:9092/~/insurancedb`
- ❌ `jdbc:h2:mem:insurancedb`

### Port 9092 already in use
```bash
# Find and kill the process using port 9092
lsof -ti:9092 | xargs kill -9
```

### SEDA queue full / backpressure
Increase `insurance.processing.seda-queue-size` or decrease `insurance.processing.page-size`.

### Out of memory
- Reduce `page-size` to 500
- Increase `-Xmx` to 4g
- Enable stream caching spool (already on by default)

### Reset and re-run processing
```sql
-- Run in H2 Console
UPDATE insurance_policy 
SET processed = FALSE, processed_at = NULL, processing_batch_id = NULL, remarks = NULL
WHERE processed = TRUE;
```

---

## License
MIT — for POC / educational purposes.
