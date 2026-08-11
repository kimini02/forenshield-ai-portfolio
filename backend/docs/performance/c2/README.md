# C2 Dashboard Stats Performance Experiment and Final Decision

This directory preserves the reproducible Before baseline, isolated design experiments, and final decision for `GET /api/v1/evidences/stats`.

## Document structure

- **Before:** environment, seed, COUNT 4 + Local Cache baseline, and execution plans
- **Aggregate Query experiment:** STEP 2A and STEP 2B
- **Single-flight experiment:** STEP 2C
- **Multi JVM experiment:** STEP 2D
- **Redis Shared Cache comparison:** STEP 2E, preserved as a rejected alternative
- **Final Decision / Final After / Remaining Trade-offs:** finalization sections at the end

## Fixed source points

- Backend: `39317431f025a780fa25f74fd23fbb332b559bed`
- Infra: `ccbfbb41ae90719115a4018f78e4db3205e52372`
- Backend deployment manifest at the Infra SHA declares 2 replicas.
- The Service manifest has no `sessionAffinity` setting. This records the manifest state; no production cluster traffic was generated.

## Environment and seed

- PostgreSQL 16.13, OpenJDK 22.0.2, `-Xms256m -Xmx1024m`
- Isolated PostgreSQL on local port 55433; the existing `waiting_db` was not used or changed.
- One test user owns 30,000 Evidence rows and 90,000 AnalysisRequest rows.
- Status distribution: QUEUED 7,500; ANALYZING 7,500; COMPLETED 7,500; FAILED 67,500.
- AnalysisResult distribution: HIGH 2,500; MEDIUM 2,500; LOW 2,500.
- All 30,000 Evidence rows have `deleted_at IS NULL`.
- The repository migrations did not create every table required by the current entities in this empty database. Hibernate `ddl-auto=update` was run once to complete the isolated schema; every measured Backend start used `ddl-auto=validate`.

The deterministic seed is [sql/seed-c2.sql](sql/seed-c2.sql). Run it only against the isolated database:

```bash
docker cp docs/performance/c2/sql/seed-c2.sql forenshield-c2-postgres:/tmp/seed-c2.sql
docker exec forenshield-c2-postgres \
  psql -U c2_test -d forenshield_c2 -f /tmp/seed-c2.sql
```

## Backend execution conditions

The measured JAR was built from the fixed Backend SHA. Database/JWT values are placeholders and must remain local:

```bash
JAVA_TOOL_OPTIONS='-Xms256m -Xmx1024m' \
SERVER_PORT=18080 \
SPRING_PROFILES_ACTIVE=local \
SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55433/forenshield_c2' \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
SPRING_DATASOURCE_USERNAME=c2_test \
SPRING_DATASOURCE_PASSWORD='<C2_DB_PASSWORD>' \
SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
SPRING_JPA_SHOW_SQL=false \
JWT_SECRET_KEY='<LOCAL_JWT_SECRET>' \
AUTH_REFRESH_ENABLED=false \
ANALYSIS_STALE_REAPER_ENABLED=false \
HLS_PACKAGING_ENABLED=false \
BLOCKCHAIN_ANCHOR_ENABLED=false \
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

The stale reaper was disabled so the deterministic QUEUED/ANALYZING seed could not be mutated during measurement. This is an isolated-environment setting, not an application code change.

## Measurement procedure

### Cold cache

Each of the 3 runs restarted the Backend JVM, authenticated normally, reset `pg_stat_statements` immediately before the stats request, and issued one request. Login SQL executed before the reset. The JWT was kept only in a shell variable and was not written here.

| Run | HTTP | COUNT calls | COUNT total execution | Auth user SELECT |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 313.923 ms | 4 | 175.697 ms | 1 |
| 2 | 184.972 ms | 4 | 61.470 ms | 1 |
| 3 | 128.214 ms | 4 | 58.534 ms | 1 |
| Median | 184.972 ms | 4 | 61.470 ms | 1 |

Run 1 includes a colder PostgreSQL buffer state than runs 2 and 3. All three runs are cold with respect to `DashboardStatsCache`; the PostgreSQL process was intentionally not restarted between runs.

### Warm cache

The cache was filled by a Cold request, `pg_stat_statements` was reset, and 100 same-user requests completed within the 30-second TTL:

```bash
BASE_URL=http://127.0.0.1:18080 \
ACCESS_TOKEN='<EPHEMERAL_ACCESS_TOKEN>' \
k6 run --summary-export docs/performance/c2/results/raw/warm-summary.json \
  docs/performance/c2/k6/stats-warm.js
```

| Requests | p50 | p95 | p99 | Mean | Throughput | Failures | Stats COUNT | Auth SELECT |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 4.1945 ms | 8.2231 ms | 11.25647 ms | 4.87787 ms | 197.947287 rps | 0% | 0 | 100 |

`Stats COUNT = 0` does not mean there were no database queries. Authentication still loaded the user once per request.

### Simultaneous Cold MISS

The JVM was restarted before every scenario. Each VU executed one iteration for the same user:

```bash
BASE_URL=http://127.0.0.1:18080 \
ACCESS_TOKEN='<EPHEMERAL_ACCESS_TOKEN>' \
VUS=10 \
k6 run --summary-export docs/performance/c2/results/raw/concurrent-vu10-summary.json \
  docs/performance/c2/k6/stats-concurrent-miss.js
```

| VU/requests | p50 | p95 | p99 | Failure | Calls per COUNT | Total COUNT | requests × 4 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 189.977 ms | 189.977 ms | 189.977 ms | 0% | 1 | 4 | 4 |
| 5 | 175.701 ms | 175.9036 ms | 175.91672 ms | 0% | 5 | 20 | 20 |
| 10 | 549.039 ms | 586.50385 ms | 586.52077 ms | 0% | 10 | 40 | 40 |
| 30 | 551.57 ms | 566.4492 ms | 566.85292 ms | 0% | 10 | 40 | 120 |

VU30 did not execute 120 COUNTs. Only 10 computations reached the four COUNT methods. The current Hikari pool maximum observed from the default configuration is 10; pool queuing followed by the first completed `put` is a plausible explanation, but the calls table alone does not prove thread scheduling order.

### Two Backend JVMs

A and B used the same database and JWT secret, but separate JVMs and ports:

| Step | Latency | Stats COUNT | Auth SELECT |
| --- | ---: | ---: | ---: |
| A Cold | 513.699 ms | 4 | 1 |
| A Warm | 35.481 ms | 0 | 1 |
| B Cold | 440.153 ms | 4 | 1 |
| B Warm | 16.849 ms | 0 | 1 |

A's cached value did not warm B. After both were warm, 100 alternating A/B requests had failure rate 0%, p50 5.705 ms, p95 20.6985 ms, p99 72.50887 ms, and no additional stats COUNT calls.

Cross-instance invalidation was not executed. A normal invalidation path requires mutating analysis state and would contaminate the fixed seed. The lack of shared invalidation is established by the local `ConcurrentHashMap` structure, but stale-response duration after a mutation remains unmeasured.

## EXPLAIN (ANALYZE, BUFFERS)

Execute [sql/explain-before.sql](sql/explain-before.sql). Raw plans are in [results/raw/explain-before.txt](results/raw/explain-before.txt).

| Query | Execution | Main rows/scans | Shared buffers | Temp I/O |
| --- | ---: | --- | ---: | --- |
| totalAnalysisCount | 52.657 ms | Seq Scan requests 90,000 + Seq Scan Evidence 30,000; Hash Join 90,000 | hit 2,071 | none |
| deepfakeDetectedCount | 9.208 ms | Request bitmap 7,500; Result Seq Scan 7,500; Evidence PK loop 5,000 | hit 15,463 | none |
| completedCount | 8.520 ms | Request bitmap 7,500 + Evidence Seq Scan 30,000; Hash Join 7,500 | hit 1,338 | none |
| inProgressCount | 12.352 ms | Request bitmap 15,000 + Evidence Seq Scan 30,000; Hash Join 15,000 | hit 1,345 | none |

The most expensive standalone plan in this run was `totalAnalysisCount`. No index or SQL change was made.

## Preserved results

- [results/environment.txt](results/environment.txt)
- [results/cold.tsv](results/cold.tsv)
- [results/count-query-stats.tsv](results/count-query-stats.tsv)
- [results/warm.tsv](results/warm.tsv)
- [results/concurrent-miss.tsv](results/concurrent-miss.tsv)
- [results/two-instance.tsv](results/two-instance.tsv)
- [results/actual-count-sql.txt](results/actual-count-sql.txt)
- `results/raw/*-summary.json`: k6 machine-readable summaries
- `results/raw/*-body.json`: count-only API response fixtures; no personal data or token
- [results/raw/explain-before.txt](results/raw/explain-before.txt)

No token, production data, or local absolute path is stored in these artifacts.

## STEP 2A — Existing COUNTs vs conditional Aggregate

This is a PostgreSQL-only candidate comparison. It is not an API After measurement, and the application, Repository, cache, Redis, indexes, schema, and `work_mem` were not changed.

### Semantic equality gate

The candidate is [sql/aggregate-candidate.sql](sql/aggregate-candidate.sql). It retains these existing conditions:

- every metric: `requested_by = uploaderId` and `evidences.deleted_at IS NULL`
- completed: `analysis_requests.status = 'COMPLETED'`
- in progress: `status IN ('QUEUED', 'ANALYZING')`
- deepfake: COMPLETED plus `risk_level IN ('HIGH', 'MEDIUM')`
- result matching: `analysis_results.analysis_request_id = analysis_requests.analysis_request_id`

`analysis_results.analysis_request_id` has a UNIQUE constraint. A LEFT JOIN therefore keeps requests without results for the total/status metrics without multiplying requests. The deepfake FILTER rejects a missing result and is equivalent to the existing inner-join COUNT for that metric.

| Metric | Existing | Aggregate | Equal |
| --- | ---: | ---: | :---: |
| totalAnalysisCount | 90,000 | 90,000 | yes |
| deepfakeDetectedCount | 5,000 | 5,000 | yes |
| completedCount | 7,500 | 7,500 | yes |
| inProgressCount | 15,000 | 15,000 | yes |

### Measurement protocol

[sql/explain-aggregate.sql](sql/explain-aggregate.sql) ran one unrecorded warm-up for each alternative, then measured both alternatives three times in one `psql` session and the same PostgreSQL process. Run 2 placed Aggregate first; Runs 1 and 3 placed the four existing queries first. No PostgreSQL setting was changed.

```bash
docker cp docs/performance/c2/sql/explain-aggregate.sql \
  forenshield-c2-postgres:/tmp/explain-aggregate.sql
docker exec forenshield-c2-postgres \
  psql -U c2_test -d forenshield_c2 -P pager=off \
  -o /tmp/explain-aggregate.txt -f /tmp/explain-aggregate.sql
```

| Run | Existing four, execution sum | Aggregate execution | Difference |
| ---: | ---: | ---: | ---: |
| 1 | 54.741 ms | 30.768 ms | 23.973 ms (43.794% lower) |
| 2 | 50.246 ms | 30.041 ms | 20.205 ms (40.212% lower) |
| 3 | 50.237 ms | 29.551 ms | 20.686 ms (41.177% lower) |
| Median | 50.246 ms | 30.041 ms | 20.205 ms (40.212% lower) |

Planning-time median was 0.614 ms in total for the four queries and 0.212 ms for Aggregate.

### Plan difference

| Alternative | Main plan | Repeated scans | Shared buffers | Read/temp I/O |
| --- | --- | --- | ---: | --- |
| Existing four | Hash Join / Nested Loop / bitmap scans | Requests and Evidence are visited independently by four statements; deepfake performs 5,000 Evidence PK lookups | hit 20,217 total | read 0, temp 0 |
| Aggregate | Finalize/Partial Aggregate with 1 worker; Hash Join plus Hash Left Join | Request input 45,000 rows × 2 loops; Evidence 30,000 × 2; Result 7,500 × 2 | hit 3,290 | read 0, temp 0 |

The Aggregate plan uses one parallel worker. Its lower wall-clock execution time is therefore not evidence of proportionally lower CPU usage. It does establish fewer SQL round trips, fewer repeated buffer visits in this plan, and a lower execution-time median under this fixed DB benchmark.

### STEP 2A judgment

**1. Aggregate Query is clearly favorable for this DB-only comparison.**

The four values were identical in all metrics, the execution-time median fell from 50.246 ms to 30.041 ms, and every measured run favored Aggregate. This is not an API improvement result because the candidate has not been connected to the application.

With the unchanged get-compute-put cache structure, a VU10 simultaneous MISS would have an **expected maximum of 10 Aggregate calls**, rather than the measured Before value of 40 COUNT calls. This is an expected call count only, not a measured After result.

### STEP 2A result files

- [results/aggregate-result-check.tsv](results/aggregate-result-check.tsv)
- [results/aggregate-explain.tsv](results/aggregate-explain.tsv)
- [results/raw/explain-aggregate.txt](results/raw/explain-aggregate.txt)

## STEP 2B - Aggregate Query Application Test

This step connects only the STEP 2A Aggregate Query to the dashboard stats Cache MISS path. It is not the final C2 After structure. `DashboardStatsCache`, its 30-second TTL, invalidate behavior, Redis, single-flight, indexes, schema, and the API response contract were not changed.

### Application change

- `DashboardStatsProjection`: exposes the four aggregate columns.
- `AnalysisRequestRepository.findDashboardStatsByUploader()`: executes the native PostgreSQL FILTER Aggregate from STEP 2A.
- `EvidenceStatsService.getDashboardStats()`: replaces four Repository calls with one Aggregate call on a cache MISS.

The measured JAR was produced from Backend SHA `39317431f025a780fa25f74fd23fbb332b559bed` plus exactly these three application-file changes in an isolated worktree. This excludes unrelated local working-tree changes from the measurement.

### Regression tests

- PostgreSQL 16 Testcontainers verifies COMPLETED, QUEUED, ANALYZING, FAILED, HIGH, MEDIUM, LOW, deleted Evidence exclusion, a COMPLETED request without AnalysisResult, and uploader separation.
- A service unit test verifies that a cache MISS calls `findDashboardStatsByUploader()` exactly once and caches the unchanged response DTO.
- Existing `EvidenceControllerTest` verifies the four API response fields.
- Full result: 311 tests, 0 failures, 0 errors, 0 skipped.

### Cold API

Each run restarted the Backend JVM, authenticated normally, and reset `pg_stat_statements` immediately before one stats request.

| Run | API latency | Aggregate calls | Aggregate execution | Auth SELECT | Total SELECT |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 167.332 ms | 1 | 88.552 ms | 1 | 2 |
| 2 | 211.351 ms | 1 | 57.182 ms | 1 | 2 |
| 3 | 163.018 ms | 1 | 64.988 ms | 1 | 2 |
| Median | 167.332 ms | 1 | 64.988 ms | 1 | 2 |

| Metric | Before | STEP 2B Aggregate | Change |
| --- | ---: | ---: | ---: |
| Cold API median | 184.972 ms | 167.332 ms | 9.537% lower |
| Stats query calls | 4 | 1 | 75% fewer |
| Stats query execution median | 61.470 ms | 64.988 ms | 5.723% higher |
| Total SELECTs | 5 | 2 | 60% fewer |

The STEP 2A warm DB-plan advantage did not reproduce in the independent Cold API starts: the Aggregate DB execution median was slightly higher. The API median was lower, but three samples and the observed run-to-run spread do not justify claiming a strong API latency improvement.

### Warm API

After the third Cold request filled the cache, 100 requests completed within the TTL.

| Metric | Before | STEP 2B Aggregate |
| --- | ---: | ---: |
| p50 | 4.1945 ms | 5.2355 ms |
| p95 | 8.2231 ms | 9.33375 ms |
| p99 | 11.25647 ms | 56.74347 ms |
| Mean | 4.87787 ms | 6.98222 ms |
| Throughput | 197.947287 rps | 139.077996 rps |
| Failure rate | 0% | 0% |
| Stats query calls | 0 | 0 |
| Auth SELECT calls | 100 | 100 |

The cache-hit code path was unchanged and no Aggregate Query ran. The worse tail in this single local run is recorded as observed variability, not attributed to the Aggregate Query.

### Simultaneous Cold MISS

Single-flight was deliberately not implemented. The JVM and cache were restarted before every scenario.

| VU/requests | Before COUNT calls | Aggregate calls | Before p95 | Aggregate p95 | Failure |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 4 | 1 | 189.977 ms | 169.279 ms | 0% |
| 5 | 20 | 5 | 175.9036 ms | 222.9162 ms | 0% |
| 10 | 40 | 10 | 586.50385 ms | 748.72525 ms | 0% |
| 30 | 40 | 10 | 566.4492 ms | 520.53395 ms | 0% |

The query integration effect is clear: calls fell by 75% in every scenario. The Local Cache race remains equally clear: VU10 still executed the same Aggregate Query 10 times. Latency did not improve consistently; VU5 and VU10 were slower despite fewer SQL calls. The Aggregate plan's broader, parallel execution under concurrent duplication is a possible contributor, but this experiment did not isolate CPU scheduling or parallel-worker contention.

In the final VU30 run, the 10 Aggregate calls accumulated 1,983.582 ms of DB execution time, or 198.358 ms mean per call. This is a concurrent aggregate value and must not be compared directly to single-request execution time.

### STEP 2B result files

- [results/aggregate-api-cold.tsv](results/aggregate-api-cold.tsv)
- [results/aggregate-api-warm.tsv](results/aggregate-api-warm.tsv)
- [results/aggregate-api-concurrent.tsv](results/aggregate-api-concurrent.tsv)
- `results/raw/aggregate-api-cold-run*-body.json`
- `results/raw/aggregate-api-warm-summary.json`
- `results/raw/aggregate-api-concurrent-vu*-summary.json`

### STEP 2B interpretation

- Query integration: one MISS now executes one stats query instead of four.
- API Cold latency: median improved modestly, but DB execution median did not improve.
- Remaining Local Cache issue: same-key concurrent MISS still duplicates the Aggregate Query.
- Local Cache value: Warm requests executed no stats query and remained much faster than Cold requests.
- Next comparison priority: local single-flight, before evaluating whether cross-instance consistency justifies a Shared Cache.

## STEP 2C - Local Single-flight Application Test

This step adds only per-`uploaderId` in-flight computation sharing to the STEP 2B application. It is not the final C2 After result. The Aggregate SQL, `DashboardStatsCache` data structure, 30-second TTL, cache key, invalidation calls, Redis, indexes, schema, and API response contract were not changed.

### Implementation

`EvidenceStatsService` keeps a `ConcurrentHashMap<Long, CompletableFuture<EvidenceStatsResponse>>` containing only active computations:

1. Check the existing local cache first. A Warm hit does not enter the in-flight map.
2. On a MISS, use `putIfAbsent(uploaderId, newFuture)` to elect one leader for that key.
3. The leader checks the cache again, executes the existing Aggregate Query once if still missing, caches the result, and completes the future.
4. Followers for the same key await and reuse that future's result.
5. Success and failure both remove the exact `(key, future)` entry in `finally`.

There is no global `synchronized` section. Different uploader IDs have different map entries and can compute concurrently.

### Concurrency and failure tests

`EvidenceStatsServiceTest` uses `CyclicBarrier`, `CountDownLatch`, and executors rather than sleep-only timing tests. It verifies:

- five simultaneous MISS requests for one key execute one Aggregate Query and receive the same result;
- two different keys both enter the Repository before either is released, ruling out global serialization;
- a Warm cache hit bypasses both the in-flight map and Repository;
- one leader failure is propagated to all same-key followers;
- the failed in-flight entry is removed, so the next request retries and succeeds.

PostgreSQL Aggregate equality and the existing controller response tests were rerun. The full test suite result was **315 tests, 0 failures, 0 errors, 0 skipped**.

Commands:

```bash
./gradlew test --tests com.example.demo.service.dashboard.EvidenceStatsServiceTest
./gradlew test \
  --tests com.example.demo.service.dashboard.EvidenceStatsServiceTest \
  --tests com.example.demo.repository.AnalysisRequestStatsPostgreSqlIntegrationTest \
  --tests com.example.demo.controller.EvidenceControllerTest
./gradlew test
```

### Measurement scope

The measured JAR used Backend SHA `39317431f025a780fa25f74fd23fbb332b559bed` plus only the STEP 2B Aggregate files and the STEP 2C `EvidenceStatsService` change in the isolated worktree. The environment remained PostgreSQL 16.13, OpenJDK 22.0.2, `-Xms256m -Xmx1024m`, PostgreSQL `work_mem=4MB`, and the original 30,000 Evidence / 90,000 AnalysisRequest / 7,500 AnalysisResult user fixture.

### Cold API

Each run restarted the Backend JVM, authenticated normally, reset `pg_stat_statements` after login, and issued one stats request. PostgreSQL itself remained running between runs.

| Run | API latency | Aggregate calls | Aggregate execution | Auth SELECT | Total SELECT |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 319.805 ms | 1 | 79.969 ms | 1 | 2 |
| 2 | 120.978 ms | 1 | 44.191 ms | 1 | 2 |
| 3 | 173.356 ms | 1 | 68.631 ms | 1 | 2 |
| Median | 173.356 ms | 1 | 68.631 ms | 1 | 2 |

| Metric | Before COUNT 4 | STEP 2B Aggregate | STEP 2C Single-flight |
| --- | ---: | ---: | ---: |
| Cold API median | 184.972 ms | 167.332 ms | 173.356 ms |
| Stats query calls per MISS | 4 | 1 | 1 |
| Stats query execution median | 61.470 ms | 64.988 ms | 68.631 ms |
| Total SELECTs per request | 5 | 2 | 2 |

Single-flight does not target an isolated VU1 MISS, so the Cold median spread is recorded without attributing it to the change.

### Warm API

The cache was filled immediately before 100 same-user requests within the unchanged TTL:

```bash
BASE_URL=http://127.0.0.1:18080 \
ACCESS_TOKEN='<EPHEMERAL_ACCESS_TOKEN>' \
k6 run --summary-export docs/performance/c2/results/raw/singleflight-warm-summary.json \
  docs/performance/c2/k6/stats-warm.js
```

| Requests | p50 | p95 | p99 | Mean | Throughput | Failure | Aggregate calls | Auth SELECT |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 5.7855 ms | 11.52215 ms | 20.06773 ms | 6.55095 ms | 148.271158 rps | 0% | 0 | 100 |

The Warm path still executes no stats query, but authentication still executes one user lookup per request. One local run is insufficient to attribute small Warm latency differences between STEP 2B and STEP 2C.

### Same-key simultaneous Cold MISS

The Backend JVM was restarted before every scenario. Every VU used the same authenticated user and executed one iteration:

```bash
BASE_URL=http://127.0.0.1:18080 \
ACCESS_TOKEN='<EPHEMERAL_ACCESS_TOKEN>' \
VUS=10 \
k6 run --summary-export docs/performance/c2/results/raw/singleflight-concurrent-vu10-summary.json \
  docs/performance/c2/k6/stats-concurrent-miss.js
```

| VU/requests | STEP 2B calls | STEP 2C calls | STEP 2B p95 | STEP 2C p95 | STEP 2C p99 | Failure |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1 | 1 | 169.279 ms | 154.430 ms | 154.430 ms | 0% |
| 5 | 5 | 1 | 222.9162 ms | 229.7938 ms | 229.88596 ms | 0% |
| 10 | 10 | 1 | 748.72525 ms | 148.97505 ms | 149.00781 ms | 0% |
| 30 | 10 | 1 | 520.53395 ms | 208.0603 ms | 208.27867 ms | 0% |

Same-key duplicate execution was removed in every scenario: VU5 fell from 5 to 1 Aggregate call, and VU10/VU30 fell from 10 to 1. VU10 p95 was 80.10% lower than STEP 2B under this identical local scenario. VU5 latency was 3.09% higher despite fewer calls, so the result does not support claiming uniform latency improvement at every concurrency level.

### Different-key simultaneous Cold MISS

This experiment ran after all original-fixture measurements. It did not change user_id=1's rows. Four additional isolated users each received 1,000 Evidence, 3,000 AnalysisRequest, and 250 AnalysisResult rows using [sql/seed-c2-multikey.sql](sql/seed-c2-multikey.sql). Each user had expected stats `3000/167/250/500`.

```bash
BASE_URL=http://127.0.0.1:18080 \
ACCESS_TOKENS='<FOUR_EPHEMERAL_TOKENS_COMMA_SEPARATED>' \
k6 run --summary-export docs/performance/c2/results/raw/singleflight-multikey-summary.json \
  docs/performance/c2/k6/stats-multikey-cold.js
```

| Distinct keys | Requests | Aggregate calls | Aggregate total execution | p50 | p95 | Failure |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4 | 4 | 4 | 83.297 ms | 144.269 ms | 144.40065 ms | 0% |

The API measurement confirms one Aggregate call per distinct key. The deterministic unit test provides the stronger no-global-lock proof: both different-key Repository computations reached a latch before either was allowed to complete.

### Invalidation race boundary

The existing invalidation semantics were intentionally left unchanged. If invalidation occurs after the leader has read the database but before its `cache.put`, that leader can still repopulate the cache with the snapshot it computed. This race already existed in the previous get-compute-put flow; STEP 2C neither fixes nor expands the invalidation contract. No state-mutating API experiment was performed against the fixed seed.

### STEP 2C result files

- [results/singleflight-api-cold.tsv](results/singleflight-api-cold.tsv)
- [results/singleflight-api-warm.tsv](results/singleflight-api-warm.tsv)
- [results/singleflight-api-concurrent.tsv](results/singleflight-api-concurrent.tsv)
- [results/singleflight-api-multikey.tsv](results/singleflight-api-multikey.tsv)
- `results/raw/singleflight-cold-run*-body.json`
- `results/raw/singleflight-warm-summary.json`
- `results/raw/singleflight-concurrent-vu*-summary.json`
- `results/raw/singleflight-multikey-summary.json`

No token, password, personal data, or local absolute path is stored in these artifacts.

### STEP 2C interpretation

- Same-key duplicate Aggregate execution is removed inside one Backend JVM.
- Warm-cache behavior and the 30-second TTL are unchanged.
- Different uploader IDs are not globally serialized.
- Failure sharing, cleanup, and subsequent retry are verified by deterministic tests.
- This does not share cache or in-flight state across Backend JVMs. The STEP 1.5 A/B measurement remains the evidence for per-JVM Local Cache separation.
- Shared Cache evaluation remains a separate STEP 3 decision; these results are not labeled as final C2 After measurements.

## STEP 2D - Multi JVM Local Cache Validation

This step measures the existing Aggregate Query, 30-second JVM Local Cache, and per-`uploaderId` Local Single-flight across two Backend JVMs. No Redis or Shared Cache was implemented. The Aggregate SQL, TTL, cache key, invalidation code, indexes, session affinity, and API contract were unchanged.

### Environment

- PostgreSQL 16.13, `work_mem=4MB`
- OpenJDK 22.0.2, `-Xms256m -Xmx1024m` per JVM
- Original user_id=1 fixture: 30,000 Evidence, 90,000 AnalysisRequest, 7,500 AnalysisResult
- A: `127.0.0.1:18080`
- B: `127.0.0.1:18081`
- Same JAR, database, JWT secret, user, and seed

Two temporary PostgreSQL login roles inherited the same `c2_test` permissions so `pg_stat_statements` could attribute identical Aggregate SQL calls to A and B separately. This measurement-only distinction did not change SQL or schema and the roles were removed after the experiment.

### Local Cache separation

Both JVMs started Cold. One authenticated token was accepted by both JVMs because they used the same JWT secret.

| Step | Port | API latency | Aggregate call delta | A cumulative | B cumulative | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| A Cold | 18080 | 143.213 ms | 1 | 1 | 0 | 90000/5000/7500/15000 |
| A Warm | 18080 | 9.331 ms | 0 | 1 | 0 | 90000/5000/7500/15000 |
| B Cold | 18081 | 290.025 ms | 1 | 1 | 1 | 90000/5000/7500/15000 |
| B Warm | 18081 | 8.379 ms | 0 | 1 | 1 | 90000/5000/7500/15000 |

A's populated Local Cache did not warm B. B's first request executed another Aggregate Query, while the second request to each JVM executed none.

### Distributed simultaneous Cold MISS

Both JVMs were restarted before every scenario. Requests used the same API user and were split evenly between A and B.

```bash
BASE_URL_A=http://127.0.0.1:18080 \
BASE_URL_B=http://127.0.0.1:18081 \
ACCESS_TOKEN='<EPHEMERAL_ACCESS_TOKEN>' \
VUS=10 \
k6 run --summary-export docs/performance/c2/results/raw/two-jvm-singleflight-vu10-summary.json \
  docs/performance/c2/k6/stats-two-jvm-cold.js
```

| Total requests | A/B | Total Aggregate | A calls | B calls | p50 | p95 | p99 | Failure |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2 | 1/1 | 2 | 1 | 1 | 289.827 ms | 346.5981 ms | 351.64442 ms | 0% |
| 10 | 5/5 | 2 | 1 | 1 | 381.8285 ms | 430.9293 ms | 431.06826 ms | 0% |
| 30 | 15/15 | 2 | 1 | 1 | 1,381.064 ms | 1,503.3616 ms | 1,503.54556 ms | 0% |

Local Single-flight still collapsed same-key requests inside each JVM, but did not collapse the same key across JVMs. Every scenario executed one Aggregate Query in A and one in B.

The VU30 tail increase is an observed local-host result. This experiment did not isolate CPU scheduling, two JVM memory/GC competition, connection-pool queuing, authentication, or PostgreSQL parallel-worker contention, so it is not attributed solely to the second Aggregate Query.

### One JVM versus two JVMs

| Scenario | JVMs | Requests | Aggregate calls | p95 |
| --- | ---: | ---: | ---: | ---: |
| STEP 2C same-key VU10 | 1 | 10 | 1 | 148.97505 ms |
| STEP 2D distributed VU10 | 2 | 10 | 2 | 430.9293 ms |

The two-JVM p95 was 189.26% higher in this run. The call count establishes the JVM boundary of Local Single-flight; it does not by itself explain the entire latency difference.

### Per-JVM warm-up and alternating Warm traffic

Starting from two Cold JVMs, the request order was A → B → A → B:

| Step | Latency | Aggregate delta |
| --- | ---: | ---: |
| A1 | 256.158 ms | 1 |
| B1 | 320.873 ms | 1 |
| A2 | 25.495 ms | 0 |
| B2 | 7.473 ms | 0 |

After both JVMs were Warm, 100 requests alternated A/B within the TTL:

| Requests | Distribution | p50 | p95 | p99 | Failure | Additional Aggregate | Auth SELECT |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | A50/B50 | 5.743 ms | 10.46925 ms | 24.45539 ms | 0% | 0 | 100 |

After each JVM paid its first-request warm-up cost, alternating Local Cache hits required no additional stats query. Authentication still loaded the user for every request.

### Cross-instance invalidation boundary

The state-changing invalidation experiment was **not executed**. The normal analysis-start path can invoke S3 copy and manifest handling, custody logging, an asynchronous Local worker, result persistence, notifications, and additional invalidations. A deterministic rollback of all database and external side effects was not guaranteed, even in the isolated database.

Code inspection establishes the structural behavior but not a measured stale duration:

- `DashboardStatsCache` is a per-JVM `ConcurrentHashMap`.
- `AnalysisService` and `AnalysisWorkerService` invalidate only their injected JVM-local cache instance.
- B has no message or shared invalidation path receiving A's invalidation.
- A B-side cache entry can therefore remain unchanged until its own 30-second entry expiry, but the actual stale duration after a mutation was **not measured**.

The configured TTL does not establish that the product permits 30 seconds of stale data.

### STEP 2D result files

- [results/two-jvm-singleflight-cold.tsv](results/two-jvm-singleflight-cold.tsv)
- [results/two-jvm-singleflight-concurrent.tsv](results/two-jvm-singleflight-concurrent.tsv)
- [results/two-jvm-singleflight-warm.tsv](results/two-jvm-singleflight-warm.tsv)
- [results/two-jvm-invalidate.tsv](results/two-jvm-invalidate.tsv)
- `results/raw/two-jvm-singleflight-vu*-summary.json`
- `results/raw/two-jvm-singleflight-warm-summary.json`

No JWT, password, personal data, or local absolute path is stored in these artifacts.

### STEP 2D interpretation

- Within each JVM, Local Single-flight reduced any number of same-key Cold requests to one Aggregate Query.
- Across two JVMs, the same key executed once per Cold JVM; the measured total was two calls.
- Each JVM had an independent first-request warm-up cost.
- Once both JVMs were Warm, 100 alternating requests added no stats Aggregate Query and failed 0%.
- Cross-instance stale duration remains unmeasured; only the absence of a shared invalidation mechanism is code-confirmed.
- These measurements provide a reason to compare a Shared Cache, but do not establish that Redis is required.


## STEP 2E - Redis Shared Cache Experiment

This step replaces only the Dashboard statistics value store with Redis while retaining the STEP 2B Aggregate Query and STEP 2C per-`uploaderId` Local Single-flight. It is a cache-architecture experiment, not the final C2 After result. No distributed lock, Redisson, Pub/Sub, L1 Local Cache, SQL/index change, TTL change, or API contract change was introduced.

### Implementation

- Existing `StringRedisTemplate` and Lettuce dependencies are reused; no Redis client was added.
- Key: `DASHBOARD:STATS:{uploaderId}`
- Value: readable JSON containing `totalAnalysisCount`, `deepfakeDetectedCount`, `completedCount`, and `inProgressCount`
- TTL: 30 seconds, equal to the previous Local Cache TTL
- Read path: Redis GET → Local Single-flight → leader Redis double check → Aggregate Query → Redis SET
- Invalidation: the existing `invalidate(uploaderId)` call sites now delete the shared key.
- Failure policy: Redis GET is treated as a cache miss. The API obtains the Aggregate result from PostgreSQL, and Redis SET/DELETE failure is logged without replacing the DB result.
- Invalid JSON is treated as a miss and deleted on a best-effort basis.

The first outage trial exposed a required boundary condition: catching a Redis exception did not help while the client was still waiting on its default connection/command timeout; the HTTP client itself timed out at 30.005 seconds before the Aggregate Query began. Dashboard fallback was then bounded with configurable local/prod Redis connect and command timeouts, both defaulting to 500 ms. With that configuration the repeat outage request returned HTTP 200 in 1,691.916 ms. The preliminary 30-second trial is an implementation finding, not a final failure-latency benchmark.

Changed files for STEP 2E:

- `src/main/java/com/example/demo/service/dashboard/DashboardStatsCache.java`
- `src/main/resources/application-local.yaml`
- `src/main/resources/application-prod.yaml`
- `src/test/java/com/example/demo/service/dashboard/DashboardStatsCacheTest.java`
- `src/test/java/com/example/demo/service/dashboard/DashboardStatsCacheRedisIntegrationTest.java`
- STEP 2E result files under this directory

### Environment and commands

- PostgreSQL 16.13, `work_mem=4MB`, same STEP 2D seed and database
- Redis 7.4.10 (`redis:7.4-alpine`) in a dedicated C2 container
- OpenJDK 22.0.2, `-Xms256m -Xmx1024m`
- One JVM for single-instance tests; A `18080` and B `18081` for shared-cache tests
- 30,000 Evidence, 90,000 AnalysisRequest, 7,500 AnalysisResult

The token remained an ephemeral shell value and was never written to a result file. Representative runs:

```bash
# One-JVM same-key Cold MISS
BASE_URL=http://127.0.0.1:18080 \
ACCESS_TOKEN='<EPHEMERAL_ACCESS_TOKEN>' \
VUS=10 \
k6 run --summary-export docs/performance/c2/results/raw/redis-single-jvm-vu10-summary.json \
  docs/performance/c2/k6/stats-concurrent-miss.js

# Two-JVM simultaneous Cold MISS
BASE_URL_A=http://127.0.0.1:18080 \
BASE_URL_B=http://127.0.0.1:18081 \
ACCESS_TOKEN='<EPHEMERAL_ACCESS_TOKEN>' \
VUS=10 \
k6 run --summary-export docs/performance/c2/results/raw/redis-two-jvm-vu10-summary.json \
  docs/performance/c2/k6/stats-two-jvm-cold.js

# Two-JVM alternating Warm traffic
BASE_URL_A=http://127.0.0.1:18080 \
BASE_URL_B=http://127.0.0.1:18081 \
ACCESS_TOKEN='<EPHEMERAL_ACCESS_TOKEN>' \
k6 run --summary-export docs/performance/c2/results/raw/redis-two-jvm-warm-summary.json \
  docs/performance/c2/k6/stats-alternating.js
```

Each Cold scenario deleted the statistics key and reset Redis command statistics and `pg_stat_statements`. Each concurrent scenario restarted the relevant Backend JVMs. All calls used the same API user and expected `90000/5000/7500/15000` response.

### Tests

`sh gradlew test` completed successfully: 322 tests, 0 failures, 0 errors, 0 skipped.

The six unit tests cover readable JSON and TTL, all-field restoration, DELETE invalidation, malformed-value removal, GET failure as miss, and SET failure tolerance. A Redis 7.4 Testcontainers integration test uses two `DashboardStatsCache` instances to establish A PUT → B HIT → A invalidate → B MISS → B Aggregate refresh → A HIT of the refreshed value. Existing single-flight tests continue to verify same-JVM duplicate suppression, follower result/error sharing, cleanup, retry, and different-key independence.

This component test verifies shared key deletion, not transaction commit timing or a full analysis workflow.

### Single-JVM Cold and Warm

Independent Cold runs restarted the Backend JVM. Run 1 had a much higher Aggregate execution time than runs 2 and 3; the available evidence does not isolate its cause, so it is retained rather than discarded.

| Run | API latency | Redis GET | Redis SET | Aggregate calls | Aggregate execution | Auth SELECT |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 865.541 ms | 2 | 1 | 1 | 621.352 ms | 1 |
| 2 | 196.903 ms | 2 | 1 | 1 | 47.816 ms | 1 |
| 3 | 175.289 ms | 2 | 1 | 1 | 32.824 ms | 1 |
| Median | 196.903 ms | 2 | 1 | 1 | 47.816 ms | 1 |

Three Cold observations are not used to report p95 or p99.

| Cache | Requests | p50 | p95 | p99 | Average | Throughput | Failure | Aggregate | Auth SELECT |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Local, STEP 2C | 100 | 5.7855 ms | 11.52215 ms | 20.06773 ms | 6.55095 ms | 148.271158 req/s | 0% | 0 | 100 |
| Redis, STEP 2E | 100 | 12.1415 ms | 27.3321 ms | 52.47583 ms | 17.1639 ms | 56.937719 req/s | 0% | 0 | 100 |

Redis Warm p95 was 137.21% higher than the single-JVM Local Cache result in this isolated host measurement. Redis issued 100 GETs; the statistics Aggregate remained at zero, but authentication still issued 100 user SELECTs.

One-JVM VU10 Cold produced 11 Redis GETs (initial GET for all ten requests plus the leader double check), one SET, one Aggregate Query, p95 682.0194 ms, and 0% failures. The high latency includes fresh-JVM simultaneous-request costs and is not attributed solely to Redis.

### A Warm to B

With both JVMs running and the Redis key initially absent:

| Step | API latency | Redis GET delta | Redis SET delta | A Aggregate delta | B Aggregate delta |
| --- | ---: | ---: | ---: | ---: | ---: |
| A first request | 713.148 ms | 2 | 1 | 1 | 0 |
| B first request | 746.258 ms | 1 | 0 | 0 | 0 |

B returned the exact statistics that A stored and executed no Aggregate Query. This is the measured difference from STEP 2D, where B's first request executed its own Aggregate Query. The absolute latencies include each JVM's first-request initialization and are not used as Redis HIT latency estimates.

### Two-JVM simultaneous Cold MISS

| Requests | A/B | Redis GET | Redis SET | Total Aggregate | A | B | p50 | p95 | p99 | Failure |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2 | 1/1 | 3 | 1 | 1 | 1 | 0 | 547.2715 ms | 583.40155 ms | 586.61311 ms | 0% |
| 10 | 5/5 | 11 | 1 | 1 | 1 | 0 | 491.171 ms | 549.587 ms | 549.587 ms | 0% |
| 30 | 15/15 | 31 | 1 | 1 | 1 | 0 | 415.5025 ms | 465.61375 ms | 466.04797 ms | 0% |

All three measured schedules happened to let A populate Redis before B's leader reached or completed its double check, reducing the observed cross-JVM Aggregate total from STEP 2D's two calls to one. This does **not** prove that Shared Cache alone guarantees cross-JVM stampede prevention. With no distributed lock, two leaders can still both observe a Redis miss under another timing schedule.

### Two-JVM Warm traffic

| Cache | Requests | Distribution | p50 | p95 | p99 | Failure | Redis GET | Aggregate | Auth SELECT |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Local, STEP 2D | 100 | A50/B50 | 5.743 ms | 10.46925 ms | 24.45539 ms | 0% | n/a | 0 | 100 |
| Redis, STEP 2E | 100 | A50/B50 | 9.2345 ms | 21.72535 ms | 542.71058 ms | 0% | 100 | 0 | 100 |

Redis Warm p95 was 107.52% higher. The Redis run also contained an isolated tail outlier (`max=700.970 ms`), so the p99 difference must not be generalized without more repeated runs. Both structures executed zero Aggregate Query once their caches were warm.

### Shared invalidation

The Redis Testcontainers component test confirmed that A's DELETE removes the key B uses. B's following read missed, its service executed one Aggregate Query returning an updated `totalAnalysisCount=90001`, and A then read that refreshed value from Redis. This establishes shared component behavior only; it does not establish after-commit invalidation, read-after-write consistency for the real workflow, or the product's acceptable stale interval.

### Redis outage and recovery

After configuring 500 ms connect and command timeouts, Redis was stopped after a normal warm-up:

| Step | Redis | HTTP | API latency | Aggregate delta | Cache behavior |
| --- | --- | ---: | ---: | ---: | --- |
| Outage | stopped | 200 | 1,691.916 ms | 1 | GET/SET failed; DB result returned |
| Recovery first | running | 200 | 255.302 ms | 1 | two GETs, one SET |
| Recovery second | running | 200 | 26.358 ms | 0 | one GET HIT |

The outage request returned the correct response with 0% request failure in this single-call experiment. It logged two failed GET attempts and one failed SET, then returned the Aggregate result. After Redis restarted, the first request repopulated it and the next request hit it. This verifies availability fallback, not an acceptable production outage latency or logging policy.

### Structure comparison

| Item | Initial Before: COUNT 4 + Local | Aggregate + Local + Local Single-flight | Aggregate + Redis + Local Single-flight |
| --- | --- | --- | --- |
| Cold API latency | 184.972 ms median | 173.356 ms median | 196.903 ms median |
| Single-JVM Warm p50/p95/p99 | measured before Aggregate; see STEP 1.5 | 5.7855 / 11.52215 / 20.06773 ms | 12.1415 / 27.3321 / 52.47583 ms |
| Same-JVM VU10 Aggregate calls | 10 Cold MISS × 4 COUNT = 40 COUNT calls | 1 | 1 |
| Two-JVM simultaneous Cold Aggregate | not measured | 2: one per JVM | 1 in each measured VU2/VU10/VU30 schedule; not guaranteed |
| A populates, then B Aggregate | Local cache separated | 1 | 0 |
| Two-JVM Warm p95 | not measured | 10.46925 ms | 21.72535 ms |
| Shared invalidation | no | no | yes at cache-component level |
| Redis outage | not applicable | not applicable | HTTP 200 DB fallback, 1,691.916 ms in measured call |
| Additional infrastructure | none | none | Redis availability and timeout policy |

### Trade-off and decision

Local Cache had the lowest measured Warm latency and has no Redis availability or serialization dependency. Its cache and invalidation scope stop at the JVM boundary, so B must independently populate and can retain a value after A invalidates its own cache.

Redis allowed B to reuse A's value, reduced the measured two-JVM Cold Aggregate count from two to one in the tested schedules, and made key deletion shared. It also added one network GET to every hit, increased measured Warm p95, and required a bounded timeout plus DB fallback during an outage. It still does not coordinate simultaneous leaders across JVMs.

**Decision: 3. Still insufficient evidence to choose.** The measurements establish the costs and capabilities, but the product requirements needed to weigh them are not yet fixed: acceptable 30-second staleness, required cross-instance read-after-write behavior, production cache-miss frequency, and expected Backend replica count. Selecting Redis solely from the reduced Cold query count would ignore its measured Warm and failure-path costs; selecting Local Cache solely from Warm latency would ignore shared invalidation requirements.

### Remaining questions, intentionally not solved

- Cross-JVM simultaneous Redis MISS can still execute duplicate Aggregate Queries under a different schedule.
- The need for a distributed lock has not been established; none was implemented.
- Existing invalidation timing remains unchanged and was not moved to transaction after-commit.
- Read-after-write consistency for the actual analysis workflow remains unverified.
- The 30-second TTL is preserved, but its stale-data policy has not been approved as a product contract.

### STEP 2E result files

- [results/redis-cold.tsv](results/redis-cold.tsv)
- [results/redis-warm.tsv](results/redis-warm.tsv)
- [results/redis-two-jvm.tsv](results/redis-two-jvm.tsv)
- [results/redis-two-jvm-concurrent.tsv](results/redis-two-jvm-concurrent.tsv)
- [results/redis-invalidate.tsv](results/redis-invalidate.tsv)
- [results/redis-failure.tsv](results/redis-failure.tsv)
- `results/raw/redis-*.json`

No JWT, password, personal data, or local absolute path is stored in these artifacts.

## Final Decision

The selected production structure is:

```text
Request
→ JVM Local Cache GET (uploaderId, TTL 30 seconds)
→ HIT: return cached aggregate
→ MISS: uploaderId Local Single-flight
→ leader executes one conditional Aggregate Query
→ JVM Local Cache PUT
→ followers receive the same result
```

- Dashboard statistics explicitly allow a short stale interval bounded by the 30-second TTL.
- Case, Evidence, AnalysisRequest, and AnalysisResult source-of-truth consistency does not depend on this Local Cache. The cache stores only a derived dashboard view.
- Existing analysis-start/completion/failure invalidation call sites are retained.
- Redis shared cache was advantageous for cross-JVM reuse and shared key invalidation, but it increased measured Warm latency and introduced timeout, fallback, and availability policy costs. It was not selected for the current requirement.
- Shared Cache must be reconsidered if Backend replica count or cache-miss QPS grows materially, or if cross-instance read-after-write becomes a product requirement.

STEP 2E code was removed from the final operating path: `DashboardStatsCache` again uses `ConcurrentHashMap`, Dashboard Redis JSON/cache-aside logic is absent, and the experiment-only Redis timeout defaults were removed from local/prod configuration. Redis result files remain above as evidence of a considered but rejected alternative.

## Final After

### Final code and regression tests

- Conditional Aggregate Query: `AnalysisRequestRepository.findDashboardStatsByUploader()`
- Four-field projection: `DashboardStatsProjection`
- Local Single-flight: `EvidenceStatsService.getDashboardStats()`
- 30-second JVM Local Cache: `DashboardStatsCache`
- PostgreSQL correctness test verifies all four metrics, owner scope, and deleted-Evidence exclusion.
- Service tests verify one Aggregate on MISS, zero Aggregate on Warm HIT, one Aggregate for same-key concurrent MISS, parallel computation for different keys, and in-flight cleanup/retry after DB failure.
- Local cache tests verify uploader isolation and existing targeted/global invalidation behavior.

`sh gradlew test` completed with 318 tests, 0 failures, 0 errors, and 0 skipped.

### Final measurement protocol

- Recheck date: 2026-08-11 Asia/Seoul
- PostgreSQL 16.13, `work_mem=4MB`
- OpenJDK 22.0.2, `-Xms256m -Xmx1024m`
- 30,000 Evidence, 90,000 AnalysisRequest, 7,500 AnalysisResult
- One Backend JVM and the same isolated database/seed/API user used by earlier steps
- Each Cold or concurrent run restarted the JVM, authenticated normally, reset `pg_stat_statements`, then executed the statistics request.
- Warm measurement populated Local Cache first, reset SQL statistics, then sent 100 requests inside the TTL.
- VU10 was repeated three times and its median run is used in the comparison. VU1, VU5, and VU30 are single verification runs.

### Cold API

| Run | API latency | Aggregate calls | Aggregate execution | Auth SELECT | Result |
| ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 705.447 ms | 1 | 141.760 ms | 1 | 90000/5000/7500/15000 |
| 2 | 312.311 ms | 1 | 89.373 ms | 1 | 90000/5000/7500/15000 |
| 3 | 1,092.271 ms | 1 | 205.529 ms | 1 | 90000/5000/7500/15000 |
| Median | 705.447 ms | 1 | 141.760 ms | 1 | 90000/5000/7500/15000 |

The final recheck Cold median was higher than both the original 184.972 ms Before and the earlier STEP 2C 173.356 ms measurement. The Aggregate execution median itself also differed from STEP 2C (141.760 ms versus 68.631 ms). Because the local host was not CPU/IO resource-isolated and only three Cold samples exist, this recheck does not establish a code regression or a Cold-latency improvement. It does establish the expected response and one Aggregate call per Cold request.

### Warm API

| Requests | p50 | p95 | p99 | Average | Throughput | Failure | Aggregate | Auth SELECT |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 6.3215 ms | 14.2111 ms | 29.7241 ms | 8.14151 ms | 120.062144 req/s | 0% | 0 | 100 |

The original Before Warm p50/p95/p99 was 4.1945/8.2231/11.25647 ms. The Aggregate optimization is not present on a Local Cache HIT, so the higher Final Warm sample is recorded as run-to-run variation rather than attributed to the Aggregate or Single-flight code.

### Simultaneous Cold MISS

| VUs | Runs | Aggregate calls | p50 | p95 | p99 | Failure |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1 | 1 | 418.509 ms | 418.509 ms | 418.509 ms | 0% |
| 5 | 1 | 1 | 255.518 ms | 255.7568 ms | 255.80176 ms | 0% |
| 10 | 3, median run | 1 | 521.6025 ms | 521.8 ms | 521.8792 ms | 0% |
| 30 | 1 | 1 | 433.952 ms | 439.53525 ms | 439.96722 ms | 0% |

VU10 run p95 values were 585.22985, 521.8, and 491.5349 ms. Each run executed exactly one Aggregate Query and ten authentication user SELECTs.

### Final Before versus recheck

| Metric | Before: COUNT 4 + Local | Final recheck: Aggregate 1 + Local + Single-flight | Change |
| --- | ---: | ---: | ---: |
| Cold API median | 184.972 ms | 705.447 ms | +281.38% |
| Warm p50 | 4.1945 ms | 6.3215 ms | +50.71% |
| Warm p95 | 8.2231 ms | 14.2111 ms | +72.82% |
| Warm p99 | 11.25647 ms | 29.7241 ms | +164.06% |
| VU10 p95 | 586.50385 ms | 521.8 ms | 11.03% lower |
| VU10 statistics SQL | 40 COUNT calls | 1 Aggregate call | 39 fewer calls |
| VU10 failure | 0% | 0% | unchanged |

The defensible final result is the execution-count invariant: same-key VU10 reduced statistics SQL from 40 COUNT calls to one Aggregate Query and failed 0%. The 11.03% p95 reduction is the new three-run median comparison, but the large difference from the earlier STEP 2C p95 of 148.97505 ms demonstrates local-host variance. It must not be presented as a stable production latency improvement without a resource-isolated rerun.

### Final result files

- [results/final-cold.tsv](results/final-cold.tsv)
- [results/final-warm.tsv](results/final-warm.tsv)
- [results/final-concurrent.tsv](results/final-concurrent.tsv)
- [results/final-comparison.tsv](results/final-comparison.tsv)
- `results/raw/final-cold-run*-body.json`
- `results/raw/final-warm-summary.json`
- `results/raw/final-concurrent-vu*-summary.json`

## Remaining Trade-offs

- Local Cache values and invalidation are not shared across JVMs. Each Cold JVM may execute one Aggregate Query.
- A mutation handled by JVM A does not invalidate JVM B's entry; B can retain its derived value until the remaining TTL expires.
- The policy accepts this short stale interval for dashboard statistics only, not for source-of-truth case or analysis state.
- Current invalidation timing was not moved to transaction after-commit; the existing timing remains a separate consistency consideration.
- Local Single-flight suppresses duplicate work only inside one JVM.
- Redis/Shared Cache should be revisited if replica count, miss QPS, or cross-instance read-after-write requirements change.
- The Final Cold and concurrency latency recheck had substantial local-host variance. Stable latency claims require repeated resource-isolated measurements; no additional optimization is included in this finalization.
