# C4 — Concurrent Approval Task Idempotency

## Scope and baseline

- Backend SHA: `3609f5af2695fe1f5fa2d62e4dbd3752417a68dd`
- Isolated detached worktree based on the Backend SHA above
- PostgreSQL: Testcontainers `postgres:16-alpine`, server `16.13`
- Java: `17.0.14`
- Transaction isolation: PostgreSQL `READ COMMITTED`
- Report worker scheduler: disabled; duplicate-worker experiment invokes claim/process explicitly
- Run date: 2026-08-12

Run command:

```bash
TZ=UTC sh gradlew test \
  --tests 'com.example.demo.performance.c4.ConcurrentApprovalBeforeReproductionTest' \
  --console=plain
```

The test completed successfully (`3 tests`, `0 failures`, `0 errors`, `0 skipped`).
On the After branch the unchanged Before reproduction source is tagged `c4-before-reproduction`
and excluded from the default test task, matching the C3 evidence-preservation pattern. Re-run it
from the baseline SHA shown above; do not reinterpret its duplicate-row assertions as After tests.

## Code path and race window

`POST /api/v1/cases/review-decision` calls `CaseController.recordReviewDecision()`, then
`CaseReviewService.recordDecision()` (`@Transactional`, default REQUIRED). Approval calls
`ReportIssueTaskService.createPendingTasks()` (`MANDATORY`). For each eligible AnalysisResult,
`shouldSkip()` first reads the latest Report and then calls
`ReportIssueTaskRepository.existsByAnalysisResultId()`. If absent, `saveAllAndFlush()` inserts a
PENDING task.

There is no pessimistic lock or `@Version` on `CaseProfile`, no unique constraint on
`report_issue_tasks.analysis_result_id`, and no unique constraint on `reports.analysis_result_id`
or a `(report_id, anchor_type)` business key in `blockchain_anchors`.

Observed race:

```text
TX A                                      TX B
exists task for analysisResult → false    exists task for analysisResult → false
INSERT PENDING task A                     INSERT PENDING task B
COMMIT                                    COMMIT
```

The deterministic schema-level test uses two independent JDBC transactions and a CyclicBarrier
after both missing checks. Both transactions committed and PostgreSQL retained two rows.

## Actual approval API results

Each condition used a new CaseProfile, Evidence, completed AnalysisRequest and AnalysisResult.
Requests started together through a CountDownLatch and used the real Spring MVC approval path via
MockMvc. HTTP latency is recorded only as context; it is not a performance benchmark.

| Concurrent | Runs | HTTP 200 per run | Other | Final task rows per run |
| ---: | ---: | ---: | ---: | ---: |
| 2 | 3 | 2 | 0 | 2 |
| 5 | 3 | 5 | 0 | 5 |
| 10 | 3 | 10 | 0 | 10 |

Result: **Concurrent Approval Task Creation Race reproduced.** HTTP 200 did not imply one durable
task per AnalysisResult.

## Duplicate-worker impact

Two duplicate PENDING tasks were claimed in one batch, so C3 `SKIP LOCKED` did not collapse two
different task rows. The two processors were started concurrently.

| Check | Result |
| --- | ---: |
| Task rows | 2 |
| Claimed tasks | 2 |
| Report rows for AnalysisResult | 2 |
| PDF files | 2 |
| Blockchain HTTP calls | 2 |
| BlockchainAnchor rows | 2 |

R1 concurrent approval duplicate Task: **YES**. R2 duplicate Task processing: **YES**. R3 duplicate
Report, R4 duplicate file side effect, and R5 duplicate Blockchain HTTP: **YES in the final isolated
run**. An earlier run of the same worker test produced one Report/PDF/HTTP/Anchor because one worker
observed the first worker's completed Report. The side-effect duplication is therefore timing
dependent; the final run proves it is possible, not that it occurs on every duplicate-task run.

## Gatling configuration

STEP 4 adds the Gatling Gradle plugin and a dedicated `gatling` source set rooted at
`docs/performance/c4/gatling`. Main and test outputs are excluded from its classpath, so Gatling is
not a production runtime dependency. Spring dependency management initially replaced Gatling's
Netty 4.2 modules with Netty 4.1 and the runner stopped before sending a request with
`NoClassDefFoundError: io/netty/channel/IoOps`. The final Gatling-only configuration pins the
versions required by Gatling; production and ordinary test configurations are unchanged.

The simulation requires `C4_BASE_URL`, `C4_AUTH_TOKEN`, `C4_CASE_KEY`, and `C4_USERS`. It uses
`atOnceUsers`, checks HTTP 200, disables Gatling's unrelated public warm-up request, and asserts zero
failed requests. The token is acquired through the real login API and kept only in an environment
variable; no token or fixture password is stored in these results.

## Evidence

- `results/deterministic-race-before.tsv`
- `results/concurrent-approval-before.tsv`
- `results/duplicate-worker-before.tsv`
- `raw/junit-postgres-before.txt`
- `sql/verify-duplicate-tasks.sql`

Do not store JWTs, passwords, personal data, or local absolute paths in published results.

## STEP 2 design decision

The selected invariant is one durable task over the lifetime of one AnalysisResult. A FAILED task
is retried by transitioning the same row; COMPLETED and FAILED rows are not replaced. A different
AnalysisResult remains eligible for its own task.

The selected implementation is:

```text
UNIQUE (report_issue_tasks.analysis_result_id)
+
INSERT ... ON CONFLICT (analysis_result_id) DO NOTHING
```

Pessimistic locking, optimistic locking, and HTTP idempotency keys were not selected. They either
serialize a broader CaseProfile scope or do not directly encode the task business invariant.

## STEP 3 implementation

Before used a non-atomic check followed by a later insert:

```text
existsByAnalysisResultId()
→ create entities
→ saveAllAndFlush()
```

After uses a database constraint and an atomic insert. A conflict returns affected row count `0`
and remains a normal no-op, so a concurrent duplicate approval does not receive 409 or 500.
Candidate AnalysisResults are inserted in ascending ID order to keep lock acquisition order
deterministic when one case owns several results.

`CaseReviewService.recordDecision()` remains the REQUIRED transaction owner, and
`ReportIssueTaskService.createPendingTasks()` remains MANDATORY. A forced failure after the atomic
insert rolled back both CaseProfile approval and the new Task.

PostgreSQL is the production correctness source of truth. Existing H2 regression tests use a
standard `MERGE ... WHEN NOT MATCHED THEN INSERT` statement with the same unique constraint. The H2
statement has no matched UPDATE clause, so it does not reset an existing task's status.

## Migration prerequisite

Run the following before applying the unique constraint:

```sql
SELECT analysis_result_id, COUNT(*)
FROM report_issue_tasks
GROUP BY analysis_result_id
HAVING COUNT(*) > 1;
```

Any returned row requires business reconciliation. The migration deliberately raises an error and
does not delete or select a winning Task automatically.

## Deterministic race: Before / After

| Check | Before | After |
| --- | ---: | ---: |
| Concurrent transactions | 2 | 2 |
| Committed transactions | 2 | 2 |
| Failed transactions | 0 | 0 |
| Atomic affected rows | N/A | `[0, 1]` |
| Final Task rows | 2 | 1 |

The PostgreSQL unique constraint was also tested directly: a second ordinary INSERT for the same
AnalysisResult failed with SQLSTATE `23505`, while a different AnalysisResult inserted normally.
The versioned PostgreSQL migration itself was separately applied to an isolated `postgres:16-alpine`
container and created `uq_report_issue_tasks_analysis_result_id`; a duplicate INSERT was rejected.
The H2 migration file was also applied with H2 2.3.232 and rejected the same duplicate with 23505.

## Concurrent approval After

Each condition was executed three times through Controller, Security, Service, Repository, and
PostgreSQL. Latency remains contextual and is not treated as a benchmark.

| Concurrent | Run | HTTP 200 | Other | Task rows | Max latency |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 2 | 1 | 2 | 0 | 1 | 131.285ms |
| 2 | 2 | 2 | 0 | 1 | 46.040ms |
| 2 | 3 | 2 | 0 | 1 | 37.423ms |
| 5 | 1 | 5 | 0 | 1 | 164.979ms |
| 5 | 2 | 5 | 0 | 1 | 64.601ms |
| 5 | 3 | 5 | 0 | 1 | 60.214ms |
| 10 | 1 | 10 | 0 | 1 | 124.144ms |
| 10 | 2 | 10 | 0 | 1 | 95.887ms |
| 10 | 3 | 10 | 0 | 1 | 81.135ms |

The duplicate-group verification query returned zero rows after these requests.

## Downstream Before / After

| Check | Before timing run | After |
| --- | ---: | ---: |
| Task rows | 2 | 1 |
| Claimed tasks | 2 | 1 |
| Completed tasks | 2 | 1 |
| Report rows | 2 | 1 |
| PDF files | 2 | 1 |
| Blockchain HTTP calls | 2 | 1 |
| BlockchainAnchor rows | 2 | 1 |

The After result proves that duplicate downstream processing caused by duplicate Task rows was
blocked in this isolated scenario. It does not add independent unique constraints to Report or
BlockchainAnchor.

## Additional regression results

- Concurrent approval of one case with three distinct AnalysisResults returned HTTP 200 twice and
  retained exactly one Task per result, three Tasks total; no timeout or deadlock occurred.
- Sequential duplicate approval retained the original Task ID.
- Existing PENDING, PROCESSING, COMPLETED, and FAILED Tasks retained both ID and status.
- An existing ISSUED Report without a Task still skipped Task creation.
- Full Backend suite: `359 tests`, `0 failures`, `0 errors`, `0 skipped`.

## Remaining concurrency scope

This step does not solve APPROVED versus REVISION races, reviewerComment last-write-wins,
CaseProfile-wide optimistic concurrency, HTTP idempotency keys, or independent Report and
BlockchainAnchor uniqueness. Existing production duplicate rows must be reconciled before the
migration; this change does not clean them automatically.

## STEP 4

### Isolated network environment

- Backend base SHA: `3609f5af2695fe1f5fa2d62e4dbd3752417a68dd`, with the uncommitted C4 STEP 3 After changes
- Backend: actual Spring Boot socket on loopback port `18080`, Java `17.0.14`
- Gatling/Gradle launcher: Java `22.0.2`, Gatling plugin `3.15.1.2`, Gatling engine `3.15.1`
- Database: Docker `postgres:16-alpine`, PostgreSQL `16.13`, loopback port `55444`
- HTTP phase: report worker disabled so the Task row could be checked immediately
- Worker phase: only the representative VU10 run 3 Task retained; polling worker enabled
- Blockchain: loopback-only HTTP stub on port `18090`; no production DB or Blockchain used
- Every run: new reviewer, investigator, CaseProfile, Evidence, COMPLETED AnalysisRequest,
  AnalysisResult, and no pre-existing Report/Task

Reproduction entry point (supply ephemeral values outside the repository):

```bash
C4_RUN_LABEL=vu10-r1 \
C4_USERS=10 \
C4_FIXTURE_PASSWORD="$EPHEMERAL_FIXTURE_PASSWORD" \
docs/performance/c4/scripts/run-gatling-verification.sh
```

### Actual network results

Each VU condition was run three times. Every OK request satisfied the explicit HTTP 200 check.
Latency is recorded only as execution context; C4 is a concurrency correctness experiment, not a
latency improvement benchmark.

| Users | Run | Requests | HTTP 200 | Other | Failed | p50 | p95 | p99 | Task rows | Duplicate groups |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2 | 1 | 2 | 2 | 0 | 0 | 587ms | 587ms | 587ms | 1 | 0 |
| 2 | 2 | 2 | 2 | 0 | 0 | 666ms | 666ms | 666ms | 1 | 0 |
| 2 | 3 | 2 | 2 | 0 | 0 | 408ms | 408ms | 408ms | 1 | 0 |
| 10 | 1 | 10 | 10 | 0 | 0 | 456ms | 456ms | 456ms | 1 | 0 |
| 10 | 2 | 10 | 10 | 0 | 0 | 445ms | 445ms | 445ms | 1 | 0 |
| 10 | 3 | 10 | 10 | 0 | 0 | 472ms | 472ms | 472ms | 1 | 0 |
| 50 | 1 | 50 | 50 | 0 | 0 | 456ms | 488ms | 513ms | 1 | 0 |
| 50 | 2 | 50 | 50 | 0 | 0 | 433ms | 466ms | 467ms | 1 | 0 |
| 50 | 3 | 50 | 50 | 0 | 0 | 454ms | 484ms | 485ms | 1 | 0 |

Immediately after every run, PostgreSQL contained exactly one PENDING Task for that run's
AnalysisResult. The Before-compatible duplicate query returned zero rows after every run.

### Representative worker verification

VU10 run 3 was selected. The other eight isolated Task rows were removed before enabling the
worker so previous runs could not affect downstream counts.

| Check | Actual result |
| --- | ---: |
| Task rows / status | 1 / COMPLETED |
| Report rows / status | 1 / ISSUED |
| PDF files | 1 |
| Blockchain HTTP calls | 1 |
| REPORT_HASH BlockchainAnchor rows / status | 1 / ANCHORED |
| Duplicate Task groups | 0 |

### HTML reports and Git policy

Nine reports were generated under `build/reports/gatling/`; their relative directories are listed
in `results/gatling-after.tsv`. The representative capture report is:

`build/reports/gatling/concurrentapprovalsimulation-20260812140308175/index.html`

The generated HTML bundle is intentionally not copied into `docs`: it is reproducible, large,
contains many generated assets, and belongs under the ignored `build` tree. The compact TSV and
sanitized raw summaries are the versionable evidence. A scan found no concrete JWT, Authorization
header value, fixture password, personal data, or local absolute filesystem path in the versionable
C4 results.

### STEP 4 regression test

`TZ=UTC sh gradlew test --rerun-tasks --console=plain` was run after adding the Gatling-only
configuration. The XML total was `359 tests`, `0 failures`, `0 errors`, `0 skipped`; the build was
successful. The `--rerun-tasks` flag ensured this was not an `UP-TO-DATE` result.

## After evidence

- `results/deterministic-race-after.tsv`
- `results/concurrent-approval-after.tsv`
- `results/duplicate-worker-after.tsv`
- `results/multi-analysis-result-after.tsv`
- `results/existing-state-after.tsv`
- `raw/junit-postgres-after.txt`
- `raw/postgres-after.txt`
- `results/gatling-after.tsv`
- `raw/gatling-vu2.txt`
- `raw/gatling-vu10.txt`
- `raw/gatling-vu50.txt`
- `raw/worker-after.txt`
- `raw/postgres-gatling-after.txt`
- `raw/full-test-step4.txt`
- `screenshots.md`
