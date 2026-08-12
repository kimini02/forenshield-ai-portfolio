# C4 portfolio capture guide

## Capture 1 — Before Race

- Source: `raw/junit-postgres-before.txt` or the matching test result.
- Crop: include the test/scenario name and the three values `concurrent=10`, `http200=10`, `taskRows=10`.
- Hide: usernames, tokens, machine paths, IDE account information, and unrelated test output.
- PPT caption: `동일 사건 승인 10건이 모두 200을 반환했지만 ReportIssueTask도 10건 생성됨`.

## Capture 2 — Deterministic Race

- Source: `results/deterministic-race-before.tsv` and the CyclicBarrier test output.
- Crop: include two independent transactions, both missing checks, both commits, and `taskRows=2`.
- Hide: container IDs, JDBC credentials, local paths, and unrelated logs.
- PPT caption: `두 트랜잭션이 모두 task 부재를 관찰한 뒤 커밋해 중복 행이 남는 경쟁 조건을 결정적으로 재현`.

## Capture 3 — Gatling After

- Source: `build/reports/gatling/concurrentapprovalsimulation-20260812140308175/index.html`.
- Crop: include simulation name `C4 Concurrent Approval After - VU 50`, Requests Total/OK/KO, and response-time table header. Latency graph는 개선 성과처럼 강조하지 않는다.
- Hide: browser profile, local absolute URL/path, unrelated tabs, token or request headers.
- PPT caption: `실제 HTTP 소켓에서 동시 승인 50건 모두 성공(OK 50, KO 0)`.

## Capture 4 — PostgreSQL After

- Source: isolated PostgreSQL query using `sql/verify-duplicate-tasks.sql`, plus the representative result query.
- Crop: one AnalysisResult의 `task_count=1` and duplicate query `(0 rows)` only.
- Hide: DB password, host details, personal data, terminal username, and absolute paths.
- PPT caption: `동일 AnalysisResult의 Task는 1건이며 전체 중복 그룹 조회 결과는 0건`.

Do not commit screenshots containing JWTs, passwords, personal data, report verification tokens,
or local absolute paths.
