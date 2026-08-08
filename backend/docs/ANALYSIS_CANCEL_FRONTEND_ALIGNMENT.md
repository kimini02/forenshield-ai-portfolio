# 분석 중단 프론트 연동 기준

본 문서는 현재 ERD/AWS DB 기준을 유지하면서 프론트엔드의 분석 시작/중단 UI와 백엔드 API를 맞추기 위한 기준을 정리한다.

## 1. 유지할 ERD 기준

`AnalysisStatus`는 현재 ERD 기준을 그대로 유지한다.

```text
QUEUED
ANALYZING
COMPLETED
FAILED
```

이번 범위에서는 아래 작업을 하지 않는다.

```text
CANCELED enum 추가
AnalysisRequests status check constraint 변경
ERD 문서 수정
AWS DB schema 수정
AnalysisRequest row 유지 방식으로 변경
```

## 2. 현재 프론트 처리 방식

프론트는 분석 중단 성공 시 서버에서 `CANCELED` 상태를 다시 조회하지 않는다.

처리 방식:

```text
DELETE /api/v1/evidences/{evidenceId}/analysis 성공
-> 로컬 상태에서 분석 상태 제거
-> 화면은 업로드 완료 / 분석 시작 가능 상태로 복귀
```

프론트가 기대하는 현재 백엔드 동작:

```text
성공: 204 No Content
실패: 에러 응답 message
```

따라서 현재 ERD 기준에서는 서버 저장 상태로 `CANCELED`를 만들 필요가 없다.

## 3. 백엔드 유지 방향

현재 구조를 유지한다.

```text
분석 중단 성공 시 AnalysisRequest row 삭제
DELETE /api/v1/evidences/{evidenceId}/analysis 성공 시 204 No Content
중단 후 GET /analysis-status 요청 시 ANALYSIS_NOT_FOUND 가능
ANALYSIS_CANCELLED CoC 로그 저장 유지
```

프론트는 중단 성공 후 상태 조회를 다시 하지 않고 로컬 상태를 비운다.

## 4. 백엔드 최소 개선 범위

ERD를 바꾸지 않고 적용 가능한 개선만 진행한다.

### 4.1 중단 가능 상태 제한

현재는 `COMPLETED`만 중단을 막는다.

개선 후 정책:

```text
QUEUED: 중단 가능
ANALYZING: 중단 가능
COMPLETED: 중단 불가
FAILED: 중단 불가
```

이유:

```text
FAILED는 이미 종료된 분석 요청이므로 중단 API로 삭제하지 않는다.
```

### 4.2 유지할 응답 형태

이번 범위에서는 프론트 main 기준에 맞춰 기존 응답을 유지한다.

```text
성공: 204 No Content
실패: 400 ANALYSIS_NOT_CANCELABLE 또는 404 EVIDENCE_NOT_FOUND
```

## 5. 추후 검토 가능 작업

프론트가 나중에 요청 단위 제어를 강화하면 아래 작업을 별도 단계로 검토한다.

```text
POST /api/v1/evidences/analyze 응답에 analysisRequestId/status/progressPercent 추가
ANALYSIS_CANCELLED CoC 로그 targetType을 ANALYSIS_REQUEST 기준으로 변경
중단 성공 응답 body 추가
```

단, `CANCELED` 서버 저장 상태는 ERD/AWS DB 변경이 필요한 작업이므로 별도 합의 후 진행한다.

## 6. 이번 1단계 완료 기준

```text
ERD/schema 수정 없음
AnalysisStatus enum 수정 없음
QUEUED 분석 요청은 중단 가능
ANALYZING 분석 요청은 중단 가능
COMPLETED 분석 요청은 중단 불가
FAILED 분석 요청은 중단 불가
중단 성공 시 기존처럼 204 No Content
sh gradlew test 통과
```
