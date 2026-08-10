# C1 사건 목록 DB 페이징 성능 기록

## 기준 SHA

- Before: `7985821e6a8e91cb9f0403658abcf0c0fc2cfeb6`
- After: `265d5cb34884af87a57a116f02fe3903b6d7ebf3`
- After commit: `refactor: C1 사건 목록 DB 페이징 적용`

After는 사건 목록 리팩토링 코드와 회귀 테스트만 포함한다. 보고서 메타데이터,
CORS, HLS 및 파일 검증 관련 작업은 이 커밋에서 제외했다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| OS/CPU | macOS, Apple Silicon |
| JDK | OpenJDK 22.0.2 |
| JVM | `-Xms256m -Xmx1024m` |
| Spring Boot | 3.5.14 |
| PostgreSQL | 16.13, Docker, aarch64 |
| PostgreSQL `work_mem` | 4MB |
| 부하 도구 | k6 1.6.1 |
| SQL 통계 | `pg_stat_statements` |
| JVM allocation | Actuator `jvm.gc.memory.allocated` |

기준 데이터는 사건 10,000건, 사건당 Evidence 3건, Evidence당 AnalysisRequest
3건이다. Evidence 30,000건, AnalysisRequest 90,000건, AnalysisResult 7,500건,
CaseProfile 10,000건을 생성했다. API 조건은 분석관, `page=0`, `size=10`,
`sort=newest`다.

각 시나리오는 워밍업 10회와 측정 3회를 분리했다. VU1은 run당 15
iteration이며 표의 p50, p95, p99는 세 run에서 구한 같은 percentile의
중앙값이다.

## Before / After

| 지표 | Before | After | 변화 |
|---|---:|---:|---:|
| content | 10 | 10 | 동일 |
| 반환 domain 행 | 157,500 | 139 | 99.912% 감소 |
| p50 | 2,148.9ms | 346.8ms | 83.9% 감소 |
| p95 | 3,234.4ms | 401.2ms | 87.6% 감소 |
| p99 | 3,641.2ms | 429.6ms | 88.2% 감소 |
| allocation/request | 276.3MiB | 0.933MiB | 99.662% 감소 |
| VU10 | OOM 재현 | 실패율 0% | 동일 1GiB heap에서 OOM 미재현 |

After 단일 요청은 SELECT 8회, `BEGIN READ ONLY` 포함 statement 9회다.
domain 행은 Evidence 30, AnalysisRequest 90, AnalysisResult 9, CaseProfile 10으로
총 139행이다. Page key와 count, User 조회를 포함한 전체 SELECT 결과는 152행이다.

## 재현 절차

### 1. PostgreSQL과 seed

`pg_stat_statements`를 preload한 PostgreSQL 16을 사용한다. 데이터베이스 주소와
인증정보는 로컬 환경 변수로 주입하며 문서나 결과 파일에 저장하지 않는다.

```bash
docker run --name c1-postgres \
  -p 55432:5432 \
  -e POSTGRES_DB=c1_performance \
  -e POSTGRES_USER=c1_test \
  -e POSTGRES_PASSWORD='<LOCAL_TEST_PASSWORD>' \
  postgres:16-alpine \
  postgres -c shared_preload_libraries=pg_stat_statements -c track_io_timing=on

docker cp docs/performance/c1/sql/seed-c1.sql c1-postgres:/tmp/seed-c1.sql
docker exec c1-postgres psql -U c1_test -d c1_performance \
  -v cases=10000 -v evidence_per=3 -v requests_per=3 \
  -v app_password_hash='<BCRYPT_HASH_FOR_LOCAL_TEST_PASSWORD>' \
  -f /tmp/seed-c1.sql
```

### 2. 애플리케이션

```bash
sh gradlew bootJar

JAVA_TOOL_OPTIONS='-Xms256m -Xmx1024m' \
SERVER_PORT=18080 \
SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55432/c1_performance' \
SPRING_DATASOURCE_USERNAME='c1_test' \
SPRING_DATASOURCE_PASSWORD='<LOCAL_TEST_PASSWORD>' \
SPRING_DATASOURCE_DRIVER_CLASS_NAME='org.postgresql.Driver' \
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT='org.hibernate.dialect.PostgreSQLDialect' \
SPRING_JPA_SHOW_SQL=false \
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE='health,metrics' \
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

JWT는 로컬 로그인 응답에서 받아 셸 환경 변수 `TOKEN`으로만 전달한다. 토큰을
스크립트, 로그, summary JSON에 기록하지 않는다.

### 3. k6

```bash
BASE_URL='http://127.0.0.1:18080' \
TOKEN='<LOCAL_TEST_JWT>' \
PATH='/api/v1/cases/me?page=0&size=10&sort=newest' \
VUS=1 ITERATIONS=15 \
SUMMARY_FILE='build/c1-after-vu1.json' \
k6 run docs/performance/c1/k6/cases-list.js
```

VU10 OOM 재현 비교는 `VUS=10 ITERATIONS=10`으로 세 번 반복했다. After 세
run은 모두 실패율 0%였다. VU30은 VU10 성공 후에만 실행했다.

## JVM allocation 측정

Actuator의 누적 `jvm.gc.memory.allocated` COUNT를 사용한다. After는 부하가
작아 측정 구간에 자연 GC가 없었으므로 `jcmd <PID> GC.run`으로 측정 전후 경계를
고정했다. 경계 GC는 k6 부하 시간에 포함하지 않았다.

```text
allocation/request =
  (allocated_after - allocated_before) / measured_requests / 1,048,576
```

15요청 세 run은 0.933, 0.867, 0.933MiB/request였고 중앙값은
0.933MiB/request다. 이 값은 누적 allocation이며 retained heap 또는 Peak Heap이
아니다.

## EXPLAIN 핵심 결과

`sql/explain-after.sql`을 PostgreSQL 16.13, `work_mem=4MB`에서
`EXPLAIN (ANALYZE, BUFFERS, SETTINGS)`로 실행했다.

| 쿼리 | 반환 행 | 핵심 계획 | 대표 실행 시간 |
|---|---:|---|---:|
| Page content | 10 | Evidence 30,000행, AnalysisRequest 90,000행 처리 | 263.857ms |
| count | 1 | Page와 동일한 후보 CTE 재계산 | 186.986ms |
| Evidence batch | 30 | Seq Scan, 29,970행 필터 제거 | 6.592ms |
| AnalysisRequest batch | 90 | Bitmap Index/Heap Scan | 0.067ms |
| CaseProfile batch | 10 | `(uploader_id, case_key)` Index Scan | 0.037ms |

`pg_stat_statements` 세 run의 실행시간 중앙값은 Page Query 154.893ms,
count query 149.046ms다. Before에서 관찰된 `external merge` 정렬은 After
계획에 없지만 Page/count에 `temp read=734`, `temp written=734`가 남아 있다.

깊은 OFFSET의 VU1 p95는 첫 페이지 401.2ms, 중간 페이지 537.7ms, 마지막
페이지 618.9ms였다. 첫 페이지의 top-level sort는 10행 top-N heapsort였지만
중간은 5,010행, 마지막은 10,000행 quicksort였다.

## 이번 작업에서 남긴 한계

- Page Query 약 150ms
- count query 약 150ms
- Page와 count의 동일 후보 계산 중복
- 깊은 OFFSET에서 p95 증가
- CTE materialization에 의한 temp I/O 잔존

이 항목은 C1 확정 커밋에서 추가 최적화하지 않았다.

## 보존 파일

- `k6/cases-list.js`: 재현용 부하 스크립트
- `sql/seed-c1.sql`: 100/1,000/10,000건 공통 seed
- `sql/explain-after.sql`: Page/count 및 상세 batch 실행 계획 SQL
- `results/before-vu1`, `results/after-vu1`: VU1 summary JSON 3회
- `results/before-vu10`, `results/after-vu10`: VU10 summary JSON 3회
- `results/before-explain.txt`, `results/after-explain.txt`: 대표 실행 계획
- `results/allocation`: After allocation 원시 counter 3회
- `results/k6-matrix.tsv`: 규모·동시성별 중앙값
- `results/returned-rows.tsv`: 단일 요청 반환 행 수
