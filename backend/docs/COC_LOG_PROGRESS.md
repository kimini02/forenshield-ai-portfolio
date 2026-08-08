# CoC 로그 구현 진행 현황

본 문서는 현재 `backend-forensic` 프로젝트에서 ERD v3 기준으로 구현한 CoC 로그 작업 범위를 정리한다.

기준 문서:

```text
docs/ERD_SPECIFICATION.md
```

---

## 1. 현재 작업 기준

이번 작업의 실제 범위는 아래 처리 흐름의 CoC 로그 저장이다.

```text
파일 업로드
SHA-256 해시 생성
메타데이터 추출
AI 분석 요청 생성
RabbitMQ 큐 등록 성공/실패
실패 상황 기록
```

분석용 복사본 생성/검증/삭제는 아직 실제 기능이 없으므로 이번 범위에서 제외한다.

---

## 2. ERD 기준 저장 방식

`CustodyLogs`는 개별 FK 컬럼을 추가하지 않는다.

따라서 아래 컬럼은 추가하지 않는다.

```text
evidenceId
fileId
analysisRequestId
```

대상 엔티티는 아래 조합으로 참조한다.

```text
targetType + targetId
```

예시:

| 처리 | targetType | targetId |
| :--- | :--- | :--- |
| 증거 업로드 | EVIDENCE | evidenceId |
| 해시 생성 | EVIDENCE | evidenceId |
| 메타데이터 추출 | EVIDENCE | evidenceId |
| 분석 요청 | ANALYSIS_REQUEST | analysisRequestId |
| RabbitMQ 큐 등록 실패 | ANALYSIS_REQUEST | analysisRequestId |

파일 해시는 `subjectHash`에 저장한다.

로그 체인은 `previousLogHash`, `currentLogHash`로 연결한다.

---

## 3. 예시 요구사항과 ERD 이벤트명 차이

일부 예시 문서에는 `FILE_UPLOADED`가 나오지만, 현재 ERD v3 기준에서는 사용하지 않는다.

실제 구현 이벤트명:

```text
EVIDENCE_UPLOADED
HASH_CREATED
METADATA_EXTRACTED
ANALYSIS_REQUESTED
ERROR_OCCURRED
```

사용하지 않는 이벤트명:

```text
FILE_UPLOADED
ORIGINAL_HASH_CREATED
QUEUE_REGISTERED
```

RabbitMQ 큐 등록 성공 여부는 별도 `QUEUE_REGISTERED` 이벤트로 저장하지 않고,
`ANALYSIS_REQUESTED`의 `eventPayloadJson`에 기록한다.

---

## 4. 완료된 작업

### 4.1 공통 CoC 저장 서비스

파일:

```text
src/main/java/com/example/demo/service/CustodyLogService.java
```

구현 내용:

```text
CustodyLogService.record(...) 추가
actorId, targetType, targetId, actionType 필수 검증
마지막 로그 currentLogHash 조회
previousLogHash 연결
currentLogHash SHA-256 생성
recordUserAction(...) 기존 기능 유지
```

검증:

```text
src/test/java/com/example/demo/service/CustodyLogServiceTest.java
```

---

### 4.2 파일 업로드/해시/메타데이터 CoC 로그

파일:

```text
src/main/java/com/example/demo/service/FileService.java
```

업로드 성공 후 저장하는 로그:

| actionType | targetType | targetId | subjectHash |
| :--- | :--- | :--- | :--- |
| EVIDENCE_UPLOADED | EVIDENCE | evidenceId | originalHashValue |
| HASH_CREATED | EVIDENCE | evidenceId | originalHashValue |
| METADATA_EXTRACTED | EVIDENCE | evidenceId | originalHashValue |

payload 예시:

```json
{
  "fileName": "sample.mp4",
  "fileType": "VIDEO",
  "mimeType": "video/mp4",
  "fileSize": 12345,
  "caseName": "사건명"
}
```

```json
{
  "hashAlgorithm": "SHA-256",
  "hashValue": "..."
}
```

```json
{
  "extractionStatus": "SUCCESS"
}
```

메타데이터 추출 실패 시 업로드는 성공 처리하고 `extractionStatus = FAILED`로 기록한다.

---

### 4.3 분석 요청 CoC 로그

파일:

```text
src/main/java/com/example/demo/service/AnalysisService.java
```

분석 요청 생성 및 RabbitMQ 큐 등록 성공 후 저장하는 로그:

| actionType | targetType | targetId | subjectHash |
| :--- | :--- | :--- | :--- |
| ANALYSIS_REQUESTED | ANALYSIS_REQUEST | analysisRequestId | originalHashValue |

payload 예시:

```json
{
  "evidenceId": 1,
  "analysisRequestId": 10,
  "status": "QUEUED",
  "caseName": "사건명",
  "queueRegistered": true,
  "queueName": "forenshield.analysis.requests"
}
```

복사본 기능이 아직 없으므로 `ANALYSIS_REQUESTED.subjectHash`는 현재 `Evidence.originalHashValue`를 사용한다.

---

### 4.4 분석 큐 등록 결과 및 실패 로그

현재 develop 기준 큐 등록 경계:

```text
src/main/java/com/example/demo/service/AnalysisJobEnqueuer.java
src/main/java/com/example/demo/messaging/RabbitMqAnalysisJobEnqueuer.java
src/main/java/com/example/demo/messaging/LocalAnalysisJobEnqueuer.java
src/main/java/com/example/demo/config/RabbitMqConfig.java
```

큐 등록 성공:

```text
AnalysisRequest.status = QUEUED 유지
ANALYSIS_REQUESTED 로그 저장
startedCount에 포함
```

큐 등록 실패:

```text
AnalysisRequest.status = FAILED
AnalysisRequest.errorCode = RABBITMQ_PUBLISH_FAILED
AnalysisRequest.errorMessage = 분석 요청 큐 등록에 실패했습니다.
ERROR_OCCURRED 로그 저장
startedCount에 포함하지 않음
```

실패 payload 예시:

```json
{
  "step": "RABBITMQ_PUBLISH",
  "errorCode": "RABBITMQ_PUBLISH_FAILED",
  "message": "분석 요청 큐 등록에 실패했습니다.",
  "evidenceId": 1,
  "analysisRequestId": 10,
  "queueName": "forenshield.analysis.requests"
}
```

민감정보는 `eventPayloadJson`에 저장하지 않는다.

---

## 5. 테스트 현황

주요 테스트 파일:

```text
src/test/java/com/example/demo/service/CustodyLogServiceTest.java
src/test/java/com/example/demo/controller/EvidenceControllerTest.java
```

검증한 내용:

```text
첫 로그 previousLogHash = null
두 번째 이후 로그 previousLogHash 체인 연결
currentLogHash 64자 lowercase SHA-256 hex
subjectHash와 currentLogHash 분리
targetType + targetId 조회
파일 업로드 시 EVIDENCE_UPLOADED/HASH_CREATED/METADATA_EXTRACTED 저장
분석 요청 성공 시 ANALYSIS_REQUESTED 저장
분석 큐 등록 성공 payload에 queueRegistered=true 저장
분석 큐 등록 실패 시 ERROR_OCCURRED 저장
분석 큐 등록 실패 시 AnalysisRequest.status=FAILED 저장
비ERD 이벤트명 미사용
```

최근 검증 명령:

```text
sh gradlew test
```

결과:

```text
BUILD SUCCESSFUL
```

---

## 6. 아직 하지 않은 작업

아래 작업은 현재 요구사항 범위가 아니며, 실제 기능이 준비된 뒤 별도 단계로 진행한다.

---

### 6.1 분석용 복사본 기능 구현 후 해야 할 작업

복사본 관련 CoC 로그는 실제 복사본 생성 기능이 있어야 저장할 수 있다.

필요한 선행 기능:

```text
분석용 복사본 생성
copyHashValue 저장
copyStoragePath 저장
copyStatus ACTIVE/DELETED 저장
copyCreatedAt 저장
copyDeletedAt 저장
원본/복사본 SHA-256 비교 검증
분석 완료 또는 실패 후 복사본 삭제
```

복사본 기능 구현 후 저장할 CoC 이벤트:

```text
ANALYSIS_COPY_CREATED
ANALYSIS_COPY_VERIFIED
ANALYSIS_COPY_DELETED
```

각 이벤트 저장 기준:

| actionType | targetType | targetId | subjectHash | storagePathAtEvent |
| :--- | :--- | :--- | :--- | :--- |
| ANALYSIS_COPY_CREATED | EVIDENCE | evidenceId | copyHashValue | copyStoragePath |
| ANALYSIS_COPY_VERIFIED | EVIDENCE | evidenceId | copyHashValue | copyStoragePath |
| ANALYSIS_COPY_DELETED | EVIDENCE | evidenceId | copyHashValue | 삭제된 copyStoragePath |

구현 시 해야 할 일:

```text
Evidence에 copy 상태 변경 도메인 메서드 추가
AnalysisCopyService 추가
원본 파일에서 분석용 복사본 생성
복사본 SHA-256 계산
originalHashValue == copyHashValue 검증
검증 성공 시 ANALYSIS_COPY_VERIFIED 저장
검증 실패 시 ERROR_OCCURRED 저장
검증 실패 시 RabbitMQ publish 미실행
분석 종료 후 복사본 삭제
삭제 성공 시 ANALYSIS_COPY_DELETED 저장
삭제 실패 시 ERROR_OCCURRED 저장
```

복사본 관련 실패 로그:

| 실패 상황 | actionType | 권장 step | 권장 errorCode |
| :--- | :--- | :--- | :--- |
| 복사본 생성 실패 | ERROR_OCCURRED | ANALYSIS_COPY_CREATED | ANALYSIS_COPY_CREATE_FAILED |
| 복사본 해시 검증 실패 | ERROR_OCCURRED | ANALYSIS_COPY_VERIFIED | ANALYSIS_COPY_VERIFY_FAILED |
| 복사본 삭제 실패 | ERROR_OCCURRED | ANALYSIS_COPY_DELETED | ANALYSIS_COPY_DELETE_FAILED |

주의:

```text
복사본을 실제로 만들지 않았으면 ANALYSIS_COPY_CREATED 저장 금지
복사본 해시 검증을 실제로 하지 않았으면 ANALYSIS_COPY_VERIFIED 저장 금지
복사본을 실제로 삭제하지 않았으면 ANALYSIS_COPY_DELETED 저장 금지
```

---

### 6.2 AI 분석 실행 상태 기능 구현 후 해야 할 작업

AI 서버 또는 워커가 실제 분석을 시작/완료/실패 처리할 수 있어야 저장할 수 있다.

필요한 선행 기능:

```text
RabbitMQ consumer 또는 AI gateway callback
AnalysisRequest.status = ANALYZING 변경 흐름
AnalysisRequest.status = COMPLETED 변경 흐름
AnalysisRequest.status = FAILED 변경 흐름
AnalysisResults 저장
AnalysisModuleResults 저장
분석 실패 errorCode/errorMessage 저장
```

분석 상태 기능 구현 후 저장할 CoC 이벤트:

```text
ANALYSIS_STARTED
ANALYSIS_COMPLETED
ANALYSIS_FAILED
```

각 이벤트 저장 기준:

| actionType | targetType | targetId | subjectHash | storagePathAtEvent |
| :--- | :--- | :--- | :--- | :--- |
| ANALYSIS_STARTED | ANALYSIS_REQUEST | analysisRequestId | copyHashValue 또는 originalHashValue | copyStoragePath 또는 originalStoragePath |
| ANALYSIS_COMPLETED | ANALYSIS_RESULT | analysisResultId | copyHashValue 또는 originalHashValue | copyStoragePath 또는 originalStoragePath |
| ANALYSIS_FAILED | ANALYSIS_REQUEST | analysisRequestId | copyHashValue 또는 originalHashValue | copyStoragePath 또는 originalStoragePath |

구현 시 해야 할 일:

```text
분석 시작 시 AnalysisRequest.status = ANALYZING
분석 시작 시 ANALYSIS_STARTED 저장
분석 완료 시 AnalysisResults 저장
모듈별 결과가 있으면 AnalysisModuleResults 저장
분석 완료 시 AnalysisRequest.status = COMPLETED
분석 완료 시 ANALYSIS_COMPLETED 저장
분석 실패 시 AnalysisRequest.status = FAILED
분석 실패 시 errorCode/errorMessage 저장
분석 실패 시 ANALYSIS_FAILED 저장
필요하면 ERROR_OCCURRED도 함께 저장할지 정책 결정
```

주의:

```text
분석이 실제로 시작되지 않았으면 ANALYSIS_STARTED 저장 금지
분석 결과가 실제로 저장되지 않았으면 ANALYSIS_COMPLETED 저장 금지
분석 실패 상태가 실제로 기록되지 않았으면 ANALYSIS_FAILED 저장 금지
```

---

### 6.3 보고서 기능 구현 후 해야 할 작업

PDF 또는 분석 보고서 생성/다운로드 기능이 있어야 저장할 수 있다.

필요한 선행 기능:

```text
Reports 엔티티 저장
보고서 파일 생성
보고서 저장 경로 관리
보고서 해시 생성
보고서 다운로드 API
```

보고서 기능 구현 후 저장할 CoC 이벤트:

```text
REPORT_CREATED
REPORT_DOWNLOADED
```

각 이벤트 저장 기준:

| actionType | targetType | targetId | subjectHash | storagePathAtEvent |
| :--- | :--- | :--- | :--- | :--- |
| REPORT_CREATED | REPORT | reportId | reportHash | reportStoragePath |
| REPORT_DOWNLOADED | REPORT | reportId | reportHash | reportStoragePath |

구현 시 해야 할 일:

```text
보고서 생성 완료 시 reportHash 계산
Reports row 저장
REPORT_CREATED 저장
보고서 다운로드 성공 시 REPORT_DOWNLOADED 저장
보고서 생성/다운로드 실패 시 ERROR_OCCURRED 저장
```

---

### 6.4 상세 페이지/관리자/보고서 조회 연동 정리

CoC 로그 저장이 충분히 쌓인 뒤 조회 화면과 보고서에 연결한다.

해야 할 일:

```text
EvidenceDetailService fallback 로그 최소화 또는 제거
EVIDENCE 기준 CoC 타임라인 표시
ANALYSIS_REQUEST 기준 CoC 타임라인 표시
관리자 CoC 로그 검색 조건 정리
PDF 보고서에 CoC 감사 이력 포함
eventPayloadJson 주요 필드 표시 방식 정리
```

조회 기준:

```text
증거 상세: targetType = EVIDENCE, targetId = evidenceId
분석 요청 상세: targetType = ANALYSIS_REQUEST, targetId = analysisRequestId
분석 결과 상세: targetType = ANALYSIS_RESULT, targetId = analysisResultId
보고서 상세: targetType = REPORT, targetId = reportId
```

---

### 6.5 장기적으로 검토할 작업

운영 안정성을 위해 나중에 검토할 수 있는 항목이다.

```text
CoC 로그 append-only 정책 강화
CustodyLogs 수정/삭제 방지 정책
해시 체인 검증 API
기간별 해시 체인 무결성 검사 배치
ERROR_OCCURRED 표준 errorCode 목록 정리
eventPayloadJson 민감정보 필터링 공통화
clientIp 주입 방식 정리
시스템 actorId 정책 정리
```

---

## 7. 현재 저장하지 않는 이벤트

현재는 아래 이벤트를 저장하지 않는다.

```text
ANALYSIS_COPY_CREATED 저장
ANALYSIS_COPY_VERIFIED 저장
ANALYSIS_COPY_DELETED 저장
ANALYSIS_STARTED 저장
ANALYSIS_COMPLETED 저장
ANALYSIS_FAILED 저장
REPORT_CREATED 저장
REPORT_DOWNLOADED 저장
```

저장하지 않는 이유:

```text
분석용 복사본 생성 기능이 아직 없음
AI 분석 시작/완료/실패 처리 기능이 아직 없음
분석 결과 저장 흐름이 아직 없음
보고서 생성/다운로드 기능이 아직 CoC와 연결되지 않음
```

실제 기능 없이 위 이벤트를 저장하면 CoC 로그가 사실과 다른 감사 기록이 되므로 저장하지 않는다.

---

## 8. 현재 결론

현재 구현은 아래 목표를 충족한다.

```text
파일 업로드 이후 CoC 로그 저장
SHA-256 해시 생성 이후 CoC 로그 저장
메타데이터 추출 이후 CoC 로그 저장
AI 분석 요청 생성 및 RabbitMQ 큐 등록 이후 CoC 로그 저장
RabbitMQ 큐 등록 실패 시 ERROR_OCCURRED 로그 저장
해시 체인 기반 previousLogHash/currentLogHash 연결
targetType + targetId 기준 조회
```

따라서 "파일 업로드, SHA-256 해시 생성, AI 분석 요청 생성 과정을 각각 로그로 기록"하는 현재 작업 범위는 완료된 상태다.

분석 복사본 관련 CoC는 다음 별도 작업으로 분리한다.
