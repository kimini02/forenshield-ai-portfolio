# C3 STEP 1.5 - Before Failure Reproduction

이 문서는 사건 승인 트랜잭션의 현재 구조를 변경하지 않고, PDF 파일 I/O와 Blockchain HTTP 호출이 트랜잭션 유지시간 및 rollback 불가능한 side effect에 미치는 영향을 격리 환경에서 재현한 Before 자료다.

## 환경

- Backend SHA: `50525b18d0c10f151fd98ef14cfe843bea7349f1`
- PostgreSQL: Testcontainers `postgres:16-alpine`, server `16.13`
- Java: `17.0.14`
- Hikari maximum pool size: `5`
- Blockchain: `mode=http`, JDK `HttpServer` 기반 loopback stub
- PDF: 테스트 실행 시 생성한 C3 전용 임시 디렉터리
- 호출 방식: `MockMvc`로 실제 승인 API `POST /api/v1/cases/review-decision` 호출
- 트랜잭션 경계: `JpaTransactionManager` DEBUG 로그의 `Creating new transaction`부터 `Initiating transaction commit/rollback`까지
- PostgreSQL 관측: 별도 JDBC 연결에서 `pg_stat_activity.xact_start`를 약 5ms 간격으로 sampling
- Hikari 관측: 요청 전/중 최대/요청 후 active connection 수
- 실행일: 2026-08-11 (Asia/Seoul)

실행 명령:

```bash
sh gradlew test --tests "com.example.demo.performance.c3.ApprovalTransactionBeforeReproductionTest" --console=plain
```

대상 테스트는 4개 모두 통과했다(`failures=0`, `errors=0`, `skipped=0`). Testcontainers 종료 뒤 Spring context의 `create-drop` 정리 순서 때문에 종료 훅에서 연결 종료 경고가 발생했지만 테스트 결과는 성공이었다.

## 정상 승인 흐름

최소 fixture는 분석관, 배정된 검토자, CaseProfile, Evidence, COMPLETED AnalysisRequest, AnalysisResult, AnalysisModuleResult로 구성했고 기존 Report는 두지 않았다.

정상 호출 결과:

| 항목 | 결과 |
| --- | --- |
| HTTP status | 200 |
| CaseProfile | REPORT_APPROVED |
| Report | ISSUED |
| PDF | 생성됨, 108,463 bytes |
| BlockchainAnchor | ANCHORED |
| HTTP stub calls | 1 |

## Blockchain Delay vs Transaction Duration

각 지연 조건은 독립 fixture로 3회 실행했다. 아래 값은 각 조건의 중앙값이다.

| Stub delay | API latency | Transaction duration | Max transaction age | HTTP duration | Hikari active (before → max → after) | Stub calls/run |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0ms | 135.604ms | 126ms | 121.278ms | 0.179ms | 0 → 1 → 0 | 1 |
| 1,000ms | 1,146.316ms | 1,127ms | 1,138.386ms | 1,005.475ms | 0 → 1 → 0 | 1 |
| 5,000ms | 5,195.625ms | 5,172ms | 5,172.961ms | 5,005.390ms | 0 → 1 → 0 | 1 |

0ms 대비 5,000ms 조건에서 transaction duration 중앙값은 126ms에서 5,172ms로 5,046ms 증가했다. 같은 조건에서 `pg_stat_activity`로 관측한 최대 transaction age도 121.278ms에서 5,172.961ms로 증가했다.

측정 한계:

- API latency는 별도 네트워크 구간이 없는 in-process MockMvc 시간이다.
- transaction end는 실제 commit 완료 로그가 아니라 `Initiating transaction commit` 시각이다. 이 실행에서 바로 다음 commit 로그와의 차이는 수 ms였지만, 표의 값은 일관되게 commit 개시까지를 사용했다.
- `pg_stat_activity`는 약 5ms sampling이므로 실제 최대값보다 작거나 timing 차이로 transaction log duration을 조금 넘을 수 있다.
- Hikari 값은 active connection의 요청 전/측정 중 최대/요청 후 값이며 pending connection이나 동시 부하는 측정하지 않았다.

전체 run과 시각은 [results/transaction-delay.tsv](results/transaction-delay.tsv)에 보존했다.

## PDF 저장 후 DB Rollback

테스트 Spy가 최종 PDF 파일 쓰기 이후 실행되는 `ReportPublicationSnapshotService.createIfAbsent`에서 예외를 발생시켰다.

| 구분 | 최종 상태 |
| --- | --- |
| HTTP | 500 |
| CaseProfile | REVIEW_ASSIGNED로 rollback |
| Report | 0 |
| Snapshot | 0 |
| BlockchainAnchor | 0 |
| Notification | 0 |
| CustodyLog | 0 |
| Blockchain stub calls | 0 |
| PDF | 남음, 108,506 bytes |
| PDF SHA-256 | `c36b6d35a569f8c9d5568756f1c88f1205b9fd36f1c058d83a54ccaa279b4ef9` |

DB 변경은 rollback됐지만 이미 기록된 PDF 파일은 임시 저장소에 남았다. 원본은 [results/pdf-rollback.tsv](results/pdf-rollback.tsv)에 보존했다.

## Blockchain 성공 후 DB Rollback

로컬 HTTP stub이 성공 응답을 기록한 뒤, 승인 흐름 후반의 `CaseDetailAssembler.assemble`에서 테스트 Spy가 예외를 발생시켰다.

| 구분 | 최종 상태 |
| --- | --- |
| HTTP | 500 |
| CaseProfile | REVIEW_ASSIGNED로 rollback |
| Report | 0 |
| BlockchainAnchor | 0 |
| Notification | 0 |
| CustodyLog | 0 |
| Stub 성공 요청 | 1 (`REPORT_HASH`) |
| Stub payload reportId | 12 |
| PDF | 남음, 108,521 bytes |
| PDF SHA-256 | `ebfa3744cb8545e9bb5d48cd25532339822741af60bfed0f99190b6df88ad216` |

DB에는 Report와 BlockchainAnchor가 남지 않았지만 stub에는 이미 성공 요청 1회가 기록됐고 PDF도 남았다. Stub의 `subjectHash`는 위 PDF SHA-256과 같았다. 원본은 [results/anchor-rollback.tsv](results/anchor-rollback.tsv)에 보존했다.

## 측정으로 확인된 사실

- Blockchain stub 지연이 0ms에서 5,000ms로 증가할 때 승인 transaction duration 중앙값도 126ms에서 5,172ms로 증가했다.
- 같은 실험에서 PostgreSQL 최대 transaction age가 121.278ms에서 5,172.961ms로 증가했고 Hikari active connection 최대값은 1이었다.
- PDF 파일 쓰기 이후 강제 실패에서 DB 상태는 rollback됐지만 PDF 파일은 남았다.
- Blockchain HTTP 성공 이후 강제 실패에서 DB 상태는 rollback됐지만 stub의 성공 요청과 PDF 파일은 남았다.

## 아직 확인하지 않은 항목

- 동시 승인 시 Hikari pending, pool 고갈, DB lock wait 및 처리량 영향
- 같은 사건의 동시 승인 또는 클라이언트 재시도에 따른 Report/PDF/anchor 중복 여부
- 실제 Blockchain endpoint의 지연 및 장애 특성
- 30초 read timeout 초과 시 DB, 파일, anchor 상태. 운영 timeout을 바꾸지 않기 위해 이번 실험에서는 생략했다.
- 운영 환경에서 같은 현상이 장애로 이어졌는지 여부

## STEP 2 비교 후보

- **A. 짧은 DB 트랜잭션 + 트랜잭션 밖 동기 후속 처리**: connection 점유는 줄일 수 있지만, DB 상태와 후속 작업 실패를 연결하는 별도 상태 전이가 필요하다.
- **B. Commit 이후 애플리케이션 이벤트**: rollback 전 side effect는 피할 수 있지만 프로세스 종료 시 이벤트 유실과 재처리 정책을 검토해야 한다.
- **C. Outbox/Queue 기반 비동기 처리**: 후속 작업 내구성과 재처리에 유리하지만 상태 모델, 멱등성, 운영 복잡도가 증가한다.
- **D. 현재 경계 유지 + timeout/retry 정책 비교**: 변경 범위는 작지만 외부 대기 중 transaction/connection 점유와 DB-side effect 불일치는 그대로 남을 수 있다.

이번 STEP에서는 구조를 선택하거나 구현하지 않았다. 동시 승인 실험도 범위에서 제외했다.

## After 브랜치에서의 회귀 테스트 취급

`ApprovalTransactionBeforeReproductionTest`는 승인 요청 안에서 PDF와 Blockchain HTTP가 실행되던 Before 계약과 실패 상태를 재현하는 증거다. STEP 3A/3B 이후에는 승인 API가 `ReportIssueTask.PENDING` 등록까지만 보장하므로 이 테스트의 즉시 `Report.ISSUED` 기대값은 의도적으로 현재 계약과 다르다. 원본 assertions와 raw 결과는 변경하지 않고 `c3-before-reproduction` tag로 현재 After 코드의 기본 `test` task에서 분리한다. 재실행은 Before 기준 SHA의 소스에서 위 명령을 사용하며, 최종 Before/After transaction duration 측정은 STEP 3D에서 별도 수행한다.

## 자료 위치

- 전체 지연 run: [results/transaction-delay.tsv](results/transaction-delay.tsv)
- PDF rollback: [results/pdf-rollback.tsv](results/pdf-rollback.tsv)
- Anchor rollback: [results/anchor-rollback.tsv](results/anchor-rollback.tsv)
- 테스트 표준 출력 중 C3 측정 행: [raw/c3-result-lines.txt](raw/c3-result-lines.txt)
- 대표 transaction DEBUG 경계 로그: [raw/transaction-logs/representative-boundaries.txt](raw/transaction-logs/representative-boundaries.txt)
- 재현 테스트: `src/test/java/com/example/demo/performance/c3/ApprovalTransactionBeforeReproductionTest.java`

# C3 STEP 3D - After Measurement

## Design Decision

Before 재현 결과를 바탕으로 승인 트랜잭션에는 `CaseProfile.REPORT_APPROVED`와
`ReportIssueTask.PENDING`을 함께 기록하고, 보고서 발급은 DB Polling Worker가 수행하는 구조를 선택했다.
Worker는 Task claim, Report 저장, Anchor PENDING 저장, Anchor 결과 저장을 각각 짧은 트랜잭션으로 처리하며
PDF 렌더링·파일 쓰기와 Blockchain HTTP 호출은 DB 트랜잭션 밖에서 실행한다.

## After 측정 환경

- PostgreSQL: Testcontainers `postgres:16-alpine`, server `16.13`
- Java: `17.0.14`
- Hikari maximum pool size: `5`
- Blockchain: Before와 같은 JDK `HttpServer` 기반 loopback HTTP stub
- PDF: 테스트 실행 시 생성한 C3 After 전용 임시 디렉터리
- 승인 호출: MockMvc `POST /api/v1/cases/review-decision`
- Scheduler: 자동 실행 비활성화. 승인 API 완료 후 테스트가 Worker claim/process를 명시적으로 실행
- 트랜잭션 경계: `JpaTransactionManager` DEBUG 로그
- PostgreSQL 관측: `application_name=c3-after`인 애플리케이션 연결의 `pg_stat_activity.xact_start`를 약 5ms 간격으로 sampling
- Hikari 관측: 승인 중 최대 active 및 HTTP 직전/중 최대/직후 active connection
- 각 delay 조건: 독립 fixture로 3회, 표는 중앙값
- 실행일: 2026-08-11 (Asia/Seoul)

실행 명령:

```bash
sh gradlew test --tests "com.example.demo.performance.c3.ApprovalTransactionAfterMeasurementTest" --console=plain
```

## Approval Before / After

| Blockchain delay | Before Approval API | After Approval API | Before Approval TX | After Approval TX |
| ---: | ---: | ---: | ---: | ---: |
| 0ms | 135.604ms | 45.076ms | 126ms | 36ms |
| 1,000ms | 1,146.316ms | 29.643ms | 1,127ms | 19ms |
| 5,000ms | 5,195.625ms | 28.491ms | 5,172ms | 16ms |

After 승인 API는 Report 발급 완료가 아니라 `REPORT_APPROVED + ReportIssueTask.PENDING` commit까지의 시간이다.
따라서 위 latency 차이는 같은 응답 계약의 단순 최적화 수치가 아니라 API 계약을 durable 비동기 발급으로 분리한 결과다.
5초 delay 조건에서도 승인 transaction 중앙값은 16ms였고 Blockchain delay에 비례해 증가하지 않았다.

## Worker E2E와 Transaction

| Delay | Worker E2E | Blockchain HTTP | Max Worker DB TX duration |
| ---: | ---: | ---: | ---: |
| 0ms | 139.071ms | 0.169ms | 28ms |
| 1,000ms | 1,116.782ms | 1,005.572ms | 18ms |
| 5,000ms | 5,135.746ms | 5,006.341ms | 29ms |

5초 조건의 Worker E2E가 약 5.14초인 것은 stub 자체가 약 5.01초 대기했기 때문이다.
평가 대상은 Worker 총시간이 아니라 그 대기 중 DB transaction/connection 점유 여부다.

9개 run 모두 HTTP Client 진입 시 `TransactionSynchronizationManager.isActualTransactionActive()`는 `false`였다.
HTTP 직전/측정 중 최대/직후 Hikari active는 모두 `0 → 0 → 0`이었고,
`application_name=c3-after`로 제한한 PostgreSQL sampling에서도 HTTP 중 transaction age는 `0ms`였다.
약 5ms sampling은 매우 짧은 transaction을 놓칠 수 있지만, 5초 HTTP와 함께 유지되는 장기 transaction은 관측되지 않았다.

각 Worker의 개별 transaction은 Claim, 작업 데이터 조회, artifact 경로 기록, Report/Snapshot 저장,
Anchor PENDING 저장, Anchor 결과 저장, Task 완료로 구분됐다. 9개 run에서 DEBUG 로그로 측정한
개별 Worker transaction의 최대값은 51ms였다. 로그 timestamp 해상도가 ms이므로 세부 수치는 실행환경 영향을 받는다.

## PDF 저장 후 Report DB 실패와 Retry

Report persistence에 테스트 전용 예외를 주입한 첫 실행 결과:

| 항목 | Before | After 첫 실패 |
| --- | --- | --- |
| CaseProfile | REVIEW_ASSIGNED rollback | REPORT_APPROVED 유지 |
| Task | 없음 | PENDING, attemptCount=1 |
| lastError / nextRetryAt | 없음 | 모두 기록 |
| Report / Snapshot / Anchor | 0 / 0 / 0 | 0 / 0 / 0 |
| PDF | 잔존 | 잔존, 108,569 bytes |

재처리에서는 같은 Task가 `PENDING → PROCESSING → COMPLETED`, attemptCount `1 → 2`로 전이됐고
Report는 ISSUED가 됐다. 파일 경로는 첫 시도와 같았으며 해당 Evidence 디렉터리의 PDF 파일 수는 1개였다.
PDF를 다시 렌더링하므로 최종 파일 SHA-256은 첫 시도와 달랐지만, 결정적 경로 덮어쓰기로 파일 수가 증가하지 않았다.

## Blockchain 성공 후 결과 DB 실패와 stale Recovery

HTTP stub 성공 후 ANCHORED 결과 저장에 예외를 주입했을 때 Report는 ISSUED로 유지되고,
기존 catch 경로가 Anchor를 `FAILED / ANCHOR_OUTCOME_UNKNOWN`, Task를 `COMPLETED`로 기록했다.
stub 성공 호출은 1회였으며 자동 HTTP 재호출은 없었다.

stale recovery 자체는 결과 저장 실패에 이어 outcome-unknown 기록 직전 프로세스가 중단된 상황을 별도 주입했다.
중단 직후 상태는 Task `PROCESSING`, Anchor `PENDING`, stub 성공 1회였다. processing timeout 이후 recovery를 실행하면
Task는 `COMPLETED`, Anchor는 `FAILED / ANCHOR_OUTCOME_UNKNOWN`이 됐고 stub 호출 수는 계속 1회였다.

## 실제로 확인된 개선

- Blockchain 5초 지연이 승인 API transaction duration에 포함되지 않았다.
- 5초 HTTP 대기 중 Spring transaction과 동일 Worker의 PostgreSQL 장기 transaction이 관측되지 않았다.
- PDF 이후 Report DB 실패가 승인 상태를 rollback시키지 않았고, 재시도 가능한 durable Task가 남았다.
- 동일 Task 재시도는 같은 PDF 경로를 사용해 불필요한 파일 증가 없이 ISSUED까지 완료됐다.
- Blockchain 성공 여부가 불명확한 상태는 추적 가능한 Anchor row로 남고 stale recovery가 HTTP를 자동 재전송하지 않았다.

## 측정 한계와 남은 Trade-off

- After 승인 API는 Report ISSUED까지 기다리지 않으므로 Before/After API latency를 동일 응답 계약의 단순 개선율로 해석할 수 없다.
- local MockMvc, Testcontainers, loopback stub 결과이며 운영 장애나 운영 latency를 의미하지 않는다.
- PostgreSQL sampling 간격은 약 5ms라 매우 짧은 transaction age는 0으로 기록될 수 있다.
- Hikari 수치는 격리된 단일 요청의 active connection이며 동시 부하나 pending connection을 측정하지 않았다.
- PDF 파일과 DB는 하나의 원자적 commit이 아니다. 실패 파일은 결정적 경로 재시도로 덮어쓰지만 영구 실패 파일 정리 정책은 별도다.
- Blockchain exactly-once는 보장하지 않는다. `ANCHOR_OUTCOME_UNKNOWN`은 자동 재전송 대신 수동 정합성 확인이 필요하다.
- Polling 주기만큼 보고서 발급 시작이 지연될 수 있다.
- 동일 사건 동시 승인은 C4 범위다.

## After 자료 위치

- 전체 delay run과 중앙값: [results/after-transaction-delay.tsv](results/after-transaction-delay.tsv)
- PDF 실패·재시도: [results/after-pdf-retry.tsv](results/after-pdf-retry.tsv)
- Anchor 결과 불명확·복구: [results/after-anchor-recovery.tsv](results/after-anchor-recovery.tsv)
- 측정 원본 행: [raw/after/c3-after-result-lines.txt](raw/after/c3-after-result-lines.txt)
- After 테스트: `src/test/java/com/example/demo/performance/c3/ApprovalTransactionAfterMeasurementTest.java`
