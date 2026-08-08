# ForenShield AI ERD Specification v3

본 문서는 ForenShield AI의 회원가입, 증거 업로드, 메타데이터 추출, AI 분석 요청, 분석 결과 저장, PDF 보고서 저장, CoC 로그 구조를 반영한 **정규화 ERD 보완판 v3**입니다.

> 기준: 초대코드는 유지하되, CoC 로그에서는 초대코드 관련 이벤트를 제외합니다.  
> PDF 보고서는 생성 후 저장하는 구조입니다.  
> AI 분석은 증거 업로드 직후 자동 실행하지 않고, 사용자가 분석 요청 버튼을 눌렀을 때 실행됩니다.

---

## 0. 최종 정책 요약

| 구분 | 최종 결정 |
| :--- | :--- |
| 초대코드 | 1회용 코드 사용 |
| 초대코드 테이블 | 유지 |
| CoC 로그에서 초대코드 | 제외 |
| 회원가입 후 상태 | `PENDING` |
| 관리자 승인 전 로그인 | 차단 |
| 회원가입 시 role | 항상 `ROLE_USER` (화면에서 선택 없음) |
| role 종류 | `ROLE_USER`, `ROLE_ADMIN` 만 존재 |
| 관리자 계정 | 회원가입으로 생성하지 않음 (별도 생성·부여) |
| 증거 관리자 승인 | **없음** (업로드 즉시 `UPLOADED`) |
| 사건 관리 | `caseNumber`(사건 번호), `caseName`(사건명) 문자열로 유지 |
| 증거 status | `UPLOADED` / `DELETED` (`deletedAt`과 동기화) |
| 분석 상태 | `AnalysisRequests.status`에서만 관리 |
| 분석 실패 재시도 | **새 `AnalysisRequests` row 생성** (기존 row 갱신 안 함) |
| 재분석 (COMPLETED 후) | MVP 불허 |
| 같은 증거 파일 중복 업로드 | 허용 |
| 해시값 Unique | 제거 |
| 메타데이터 추출 | 업로드 직후 자동 추출 |
| AI 분석 실행 | 사용자가 분석 요청 버튼을 눌러야 실행 |
| AI 분석 결과 | PostgreSQL 저장 |
| PDF 보고서 | 생성 후 저장 |
| PDF 저장 위치 | S3 저장 권장 |
| CoC 해시 체인 | `previousLogHash`, `currentLogHash` 사용 |
| CoC 로그 대상 | 사용자, 증거, 분석 요청/결과, 보고서 중심 |
| 분석 건수 통계 | RQ-DSH-043 4카드 필드로 집계 (`totalAnalysisCount` 등) — DB 컬럼 저장 없음 |

---

## 1. 최종 테이블 목록

MVP 기준 최종 테이블은 다음과 같습니다.

```text
Users
InviteCodes
Evidences
EvidenceMetadata
AnalysisRequests
AnalysisResults
AnalysisModuleResults
Reports
CustodyLogs
```

분석 건수 통계는 별도 테이블 없이 `AnalysisRequests`·`AnalysisResults`·`Evidences`를 조인하여 계산합니다.  
**MVP 대시보드(RQ-DSH-043):** 미디어 유형별 카드가 아니라 4카드(`totalAnalysisCount`, `deepfakeDetectedCount`, `completedCount`, `inProgressCount`)를 반환합니다.

선택적으로 추후 확장 가능한 테이블은 다음과 같습니다.

```text
Cases
OrganizationUnits
UserSettings
AnalysisReasons
```

이번 MVP에서는 `Cases`를 별도 테이블로 분리하지 않고, `Evidences.caseNumber`(사건 번호)와 `Evidences.caseName`(사건명) 문자열로 관리합니다.

---

## 2. 핵심 설계 원칙

### 2.1 회원가입은 일반 가입이 아니라 계정 신청이다

ForenShield AI는 내부 사용자 전용 포렌식 분석 보조 시스템이므로, 사용자는 회원가입 직후 바로 시스템을 사용할 수 없습니다.

```text
회원가입 신청
→ Users.status = PENDING
→ 관리자 검토
→ APPROVED 처리
→ 로그인 가능
```

### 2.2 role은 로그인 권한 검증용이며, 회원가입은 항상 USER다

`role` 컬럼은 **로그인 후 권한 분기**(일반 사용자 vs 관리자 API 접근)를 위해 존재합니다.  
회원가입 화면에서는 role을 선택하지 않으며, 가입 신청 시 DB에는 **항상 `ROLE_USER`** 가 저장됩니다.

```text
role 종류: ROLE_USER, ROLE_ADMIN (2가지만)
회원가입: 무조건 ROLE_USER
ROLE_ADMIN: 회원가입 경로로 생성하지 않음 (관리자 계정 별도 생성)
로그인 시: role이 ROLE_ADMIN인지 검증하여 관리자 기능 접근 허용
```

가입 **승인**(`Users.status`)과 **권한 role**은 별개입니다.  
승인은 계정 활성화 여부이고, role은 로그인 후 API 권한 검증에 사용합니다.

### 2.3 초대코드는 가입 게이트 역할이다

초대코드는 1회용으로 사용합니다.  
사용자가 회원가입 시 초대코드를 입력하고, 유효한 코드일 때 가입 신청이 가능합니다.

다만 초대코드 발급/사용 자체는 포렌식 증거 생애주기의 핵심이 아니므로, `CustodyLogs`에는 초대코드 관련 이벤트를 남기지 않습니다.

### 2.4 증거 파일은 중복 업로드를 허용한다

같은 파일이더라도 다른 사건 번호나 다른 맥락에서 다시 등록될 수 있으므로, `originalHashValue`에 Unique 제약을 걸지 않습니다.

```text
originalHashValue NOT NULL
originalHashValue UNIQUE 제거
```

### 2.5 메타데이터는 업로드 직후 자동 추출한다

증거 파일 업로드 후 자동으로 다음 작업을 수행합니다.

```text
파일 업로드
→ SHA-256 해시 생성
→ 메타데이터 자동 추출
→ EvidenceMetadata 저장
```

### 2.6 AI 분석은 사용자가 요청해야 실행된다

증거 업로드만으로 AI 분석을 자동 실행하지 않습니다.  
사용자가 분석 요청 버튼을 눌렀을 때 `AnalysisRequests`가 생성되고 AI 분석이 시작됩니다.

분석 진행 상태(`QUEUED`, `ANALYZING`, `COMPLETED`, `FAILED`)는 `AnalysisRequests`에서만 관리합니다.  
`Evidences.status`는 `UPLOADED` / `DELETED`만 사용하여 상태 이중 관리를 방지합니다.

### 2.7 재분석은 불허, 실패 재시도는 새 요청 row 생성

분석 **완료(`COMPLETED`)** 후 재분석 UI/토글은 제공하지 않습니다.  
동일 증거에 `COMPLETED` 요청이 이미 있으면 새 분석 요청을 생성할 수 없습니다.

분석 **실패(`FAILED`)** 후 재시도는 허용하며, 기존 row를 수정하지 않고 **새 `AnalysisRequests` row를 생성**합니다.

```text
COMPLETED 존재 → 새 AnalysisRequests 생성 불가 (재분석 불허)
FAILED 존재    → 새 AnalysisRequests row 생성 (재시도)
재시도 토글/UI → 제공하지 않음 (실패 시에만 재요청 버튼)
```

### 2.8 증거 관리자 승인은 하지 않는다

증거 파일은 업로드 즉시 `Evidences.status = UPLOADED`로 저장됩니다.  
관리자의 증거 승인/반려 워크플로는 MVP 범위에 **포함하지 않습니다**.

---

## 3. Entity Specifications

---

## 3.1 Users

사용자, 분석 담당자, 관리자 계정 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `userId` | Long | PK | 사용자 고유 ID |
| `loginId` | String | Unique, Not Null | 로그인 아이디 |
| `email` | String | Unique, Not Null | 이메일, 승인 및 연락용 |
| `password` | String | Not Null | 암호화된 비밀번호 |
| `name` | String | Not Null | 실명 |
| `phone` | String | Nullable | 관리자 확인 및 연락용 연락처 |
| `organizationType` | OrgType | Not Null | 기관 유형 |
| `department` | String | Not Null | 소속 기관/부서 |
| `position` | String | Nullable | 직책/담당 업무 |
| `role` | UserRole | Not Null, Default `ROLE_USER` | 로그인 권한 (`ROLE_USER`, `ROLE_ADMIN`) |
| `status` | UserStatus | Not Null | 가입 승인 상태 |
| `darkMode` | Boolean | Default false | 다크모드 설정 |
| `inviteCodeId` | Long | FK, Nullable | 가입 시 사용한 초대코드 |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |
| `updatedAt` | LocalDateTime | Not Null | 수정 일시 |
| `deletedAt` | LocalDateTime | Nullable | 소프트 삭제 일시 |

### 정책

- 회원가입 직후 `status = PENDING`, `role = ROLE_USER` (고정)
- 승인 전 로그인 불가 (`status = APPROVED` 이후 로그인)
- `ROLE_ADMIN` 계정은 회원가입 API로 생성하지 않음
- 관리자 로그인 시 `role = ROLE_ADMIN` 여부로 관리자 API 접근 검증
- 계정 삭제는 실제 삭제가 아니라 `deletedAt` 기반 소프트 삭제 권장

---

## 3.2 InviteCodes

관리자가 발급하는 1회용 가입 코드입니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `inviteCodeId` | Long | PK | 초대코드 ID |
| `code` | String | Unique, Not Null | 초대코드 값 |
| `organizationType` | OrgType | Not Null | 코드가 속한 기관 유형 |
| `issuedBy` | Long | FK, Not Null | 발급 관리자 ID |
| `status` | InviteStatus | Not Null | 코드 상태 |
| `expiresAt` | LocalDateTime | Nullable | 만료 일시 |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |
| `usedAt` | LocalDateTime | Nullable | 사용 일시 |

### 정책

- 초대코드는 1회용
- 회원가입 신청 성공 시 `USED` 처리
- 초대코드 관련 이벤트는 `CustodyLogs`에 남기지 않음
- 초대코드 이력은 `InviteCodes.status`, `usedAt`, `issuedBy`로 관리

---

## 3.3 Evidences

업로드된 **원본** 증거 파일 정보와 **분석용 복사본**의 해시·경로·생명주기를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `evidenceId` | Long | PK | 증거 ID |
| `uploaderId` | Long | FK, Not Null | 업로드 사용자 ID |
| `caseNumber` | String | Nullable | 사건 번호 문자열 (예: `2026-서울-0123`) |
| `caseName` | String | Nullable | 사건명 (업로드 시 사용자 입력, 예: `딥페이크 유포 사건`) |
| `fileName` | String | Not Null | 원본 파일명 |
| `fileType` | FileType | Not Null | **MVP: VIDEO만 업로드** · enum: IMAGE, VIDEO, AUDIO |
| `mimeType` | String | Not Null | MIME 타입 |
| `fileSize` | Long | Not Null | 파일 크기 |
| `hashAlgorithm` | String | Not Null, Default `SHA-256` | 해시 알고리즘 |
| `originalHashValue` | String | Not Null | **원본** SHA-256 해시 (Hex 64자) |
| `originalStoragePath` | String | Not Null | 원본 저장 경로 (`original-files/{caseNumber}/{evidenceId}/...`) |
| `copyHashValue` | String | Nullable | **복사본** SHA-256 해시 (분석 요청 시 생성 후 저장) |
| `copyStoragePath` | String | Nullable | 복사본 저장 경로 (`/tmp/analysis/...` 또는 S3 copy 경로) |
| `copyStatus` | CopyStatus | Not Null, Default `NONE` | 복사본 상태 (`NONE`, `ACTIVE`, `DELETED`) |
| `copyCreatedAt` | LocalDateTime | Nullable | 복사본 생성 시각 |
| `copyDeletedAt` | LocalDateTime | Nullable | 복사본 삭제 시각 |
| `status` | EvidenceStatus | Not Null | 증거 생명주기 (`UPLOADED`, `DELETED`) |
| `uploadedAt` | LocalDateTime | Not Null | 업로드 일시 |
| `deletedAt` | LocalDateTime | Nullable | 소프트 삭제 일시 |

### 정책

- 같은 증거 파일의 여러 번 업로드를 허용하므로 `originalHashValue`에는 Unique를 걸지 않습니다.
- 분석 진행 상태(`QUEUED`, `ANALYZING`, `COMPLETED`, `FAILED`)는 **`AnalysisRequests.status`에서만** 관리합니다.
- `Evidences.status`는 업로드 완료(`UPLOADED`)와 삭제(`DELETED`)만 표현합니다.
- 복사본은 **분석 요청 시** 생성하며, 분석 완료 후 삭제 시 `copyStatus = DELETED`로 기록합니다. (`copyHashValue`는 DB에 유지)
- **관리자 증거 승인 없음** — 업로드 즉시 `UPLOADED`

### `status` ↔ `deletedAt` 동기화 규칙

`Evidences.status`와 `deletedAt`은 **둘 다 사용**하며, 아래 규칙으로 항상 일치해야 합니다.

| 상태 | `status` | `deletedAt` |
| :--- | :--- | :--- |
| 정상 (업로드됨) | `UPLOADED` | `NULL` |
| 소프트 삭제됨 | `DELETED` | `NOT NULL` (삭제 시각) |

**애플리케이션 규칙**

```text
소프트 삭제 시: status = DELETED, deletedAt = now() (동시 갱신)
복구 시(선택):   status = UPLOADED, deletedAt = NULL (동시 갱신)
```

**DB CHECK 제약 (권장)**

```sql
CHECK (
  (status = 'UPLOADED' AND deleted_at IS NULL)
  OR
  (status = 'DELETED' AND deleted_at IS NOT NULL)
)
```

---

## 3.4 EvidenceMetadata

증거 파일의 상세 메타데이터를 저장합니다.  
Evidence와 1:1 관계입니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `metadataId` | Long | PK | 메타데이터 ID |
| `evidenceId` | Long | FK, Unique, Not Null | 대상 증거 ID |
| `width` | Integer | Nullable | 가로 해상도 |
| `height` | Integer | Nullable | 세로 해상도 |
| `durationSec` | Integer | Nullable | 영상/음성 길이 |
| `fps` | Double | Nullable | 프레임레이트 |
| `codec` | String | Nullable | 코덱 정보 |
| `sampleRate` | Integer | Nullable | 오디오 샘플레이트 |
| `channels` | Integer | Nullable | 오디오 채널 수 |
| `capturedAt` | LocalDateTime | Nullable | 촬영/생성 시각 |
| `deviceInfo` | String | Nullable | 촬영 기기 정보 |
| `exifJson` | JSONB/Text | Nullable | EXIF 전체 |
| `ffprobeJson` | JSONB/Text | Nullable | ffprobe 결과 전체 |
| `readinessJson` | JSONB/Text | Nullable | AI 분석 전 화질 적합성 스냅샷 (tier, reasons, frame metrics). `source`: `FFPROBE` \| `FRAME_SAMPLE` |
| `extractionStatus` | ExtractionStatus | Not Null | 메타데이터 추출 상태 |
| `extractionError` | Text/String | Nullable | 추출 실패 시 오류 메시지 |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |

### 정책

- 업로드 직후 원본 파일 기준으로 자동 추출
- 추출 실패 시에도 업로드 자체는 성공 처리 (`extractionStatus = FAILED`)
- 일부 필드만 추출된 경우 `extractionStatus = PARTIAL`
- 정상 추출 시 `extractionStatus = SUCCESS`, `extractionError`는 null
- 업로드 직후 `readinessJson`에 ffprobe 기반 즉시 판정 저장 (`source = FFPROBE`)
- 분석 시작 전 `POST /evidences/{id}/readiness-check`로 프레임 샘플링 결과로 갱신 가능 (`source = FRAME_SAMPLE`)

---

## 3.5 AnalysisRequests

AI 분석 요청 상태를 관리합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `analysisRequestId` | Long | PK | 분석 요청 ID |
| `evidenceId` | Long | FK, Not Null | 분석 대상 증거 |
| `requestedBy` | Long | FK, Not Null | 분석 요청 사용자 |
| `status` | AnalysisStatus | Not Null | 요청 상태 |
| `requestedAt` | LocalDateTime | Not Null | 요청 시각 |
| `startedAt` | LocalDateTime | Nullable | 분석 시작 시각 |
| `completedAt` | LocalDateTime | Nullable | 분석 완료 시각 |
| `errorCode` | String | Nullable | 실패 코드 |
| `errorMessage` | String | Nullable | 실패 메시지 |

### 정책

- 증거 업로드만으로 자동 분석하지 않음
- 사용자가 분석 요청 버튼 클릭 시 **새 row 생성**
- 동일 `evidenceId`에 `status = COMPLETED`인 요청이 **이미 존재하면** 새 요청 생성 불가 (재분석 불허)
- `FAILED` 요청이 있어도 재시도 시 **기존 row를 수정하지 않고 새 row 생성**
- 재생성/재분석 토글 UI는 제공하지 않음

### DB 제약 (권장)

증거당 완료된 분석은 1건만 허용합니다.

```sql
CREATE UNIQUE INDEX uq_analysis_requests_one_completed
ON analysis_requests (evidence_id)
WHERE status = 'COMPLETED';
```

---

## 3.6 AnalysisResults

AI 분석 결과의 최종 요약 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `analysisResultId` | Long | PK | 분석 결과 ID |
| `analysisRequestId` | Long | FK, Unique, Not Null | 분석 요청 ID |
| `riskScore` | Double | Nullable | 종합 위험도 |
| `confidenceScore` | Double | Nullable | 결과 신뢰도 |
| `riskLevel` | RiskLevel | Nullable | LOW, MEDIUM, HIGH |
| `summary` | Text/String | Nullable | 분석 요약 |
| `analyzedAt` | LocalDateTime | Not Null | 분석 완료 시각 |

### 위험도 기준

```text
LOW: 0 ~ 39
MEDIUM: 40 ~ 69
HIGH: 70 ~ 100
```

---

## 3.7 AnalysisModuleResults

영상 분석 모듈별 세부 결과를 저장합니다. **MVP는 VIDEO만** 처리합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `moduleResultId` | Long | PK | 모듈 결과 ID |
| `analysisResultId` | Long | FK, Not Null | 분석 결과 ID |
| `fileType` | FileType | Nullable | IMAGE, VIDEO, AUDIO |
| `moduleName` | String | Not Null | 분석 모듈명 |
| `detected` | Boolean | Nullable | 탐지 여부 |
| `score` | Double | Nullable | 모듈 점수 |
| `confidence` | Double | Nullable | 모듈 신뢰도 |
| `modelName` | String | Nullable | 모델명 |
| `modelVersion` | String | Nullable | 모델 버전 |
| `evidenceText` | Text/String | Nullable | 분석 근거 문장 |
| `detailsJson` | JSONB/Text | Nullable | 세부 결과 JSON |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |

### moduleName 예시

```text
face_swap_detection
lip_sync_analysis
synthetic_voice_detection
generated_image_detection
metadata_analysis
```

---

## 3.8 Reports

AI 분석 결과를 기반으로 생성된 PDF 보고서 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `reportId` | Long | PK | 보고서 ID |
| `analysisResultId` | Long | FK, Not Null | 기반 분석 결과 |
| `evidenceId` | Long | FK, Not Null | 대상 증거 |
| `createdBy` | Long | FK, Not Null | 보고서 생성 사용자 |
| `reportFileName` | String | Nullable | PDF 파일명 |
| `storagePath` | String | Not Null | PDF 저장 경로 |
| `reportHash` | String | Nullable | PDF 파일 SHA-256 |
| `fileSize` | Long | Nullable | PDF 파일 크기 |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |

### 정책

- AI 분석 결과는 PostgreSQL에 저장
- PDF 파일은 S3에 저장
- Reports 테이블에는 PDF 경로, 파일명, 해시, 크기 저장
- 보고서에는 원본 파일 해시와 PDF 보고서 해시를 함께 표시

---

## 3.9 ReportPublicationSnapshots

최종 발행 시점의 보고서 입력값과 공개 요약을 보고서별 1건으로 동결합니다. 기존 분석 결과 테이블은 변경하지 않으며, 발행본을 다시 만들거나 공개 검증 정보를 조회할 때 이 스냅샷을 우선 사용합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `snapshotId` | Long | PK | 발행 스냅샷 ID |
| `reportId` | Long | FK, Unique, Not Null | 대상 발행 보고서 |
| `schemaVersion` | String | Not Null | 스냅샷 데이터 계약 버전 |
| `pdfTemplateVersion` | String | Not Null | 발행 PDF 템플릿 버전 |
| `reportInputJson` | Text | Not Null | PDF 렌더링에 사용한 전체 입력값 |
| `publicSummaryJson` | Text | Not Null | 공개가 허용된 범주형 결과 요약 |
| `artifactManifestJson` | Text | Not Null | 채택된 구간·프레임·타임라인 목록 |
| `displayPolicyJson` | Text | Not Null | 공개·비공개 필드 정책 |
| `snapshotHash` | String(64) | Not Null | 스냅샷 구성값의 SHA-256 |
| `createdAt` | LocalDateTime | Not Null | 스냅샷 생성 일시 |

### 정책

- `Reports 1:1 ReportPublicationSnapshots`
- 발행 처리 중 최초 1회만 생성하며 기존 스냅샷을 갱신하거나 덮어쓰지 않음
- 발행된 PDF 파일이 유실되어 재생성할 때 현재 분석 테이블이 아닌 스냅샷 입력값을 우선 사용
- 스키마 `1.1`의 `reportInputJson`에는 발행 시점 증거 매니페스트 서명, CoC 해시 체인·기록 건수, 증거 해시 블록체인 검증 상태를 포함
- 공개 검증에는 범주형 판정·분석 완료 시각·증거 매니페스트 서명 상태만 제공하고 사건명·파일명·담당자·수치 점수·구간·프레임은 비공개
- 스냅샷이 없는 기존 발행 보고서는 호환을 위해 기존 데이터 조회를 허용하되, 새 발행 건은 반드시 스냅샷을 생성

---

## 3.10 CustodyLogs

CoC 및 감사 로그를 저장합니다.  
초대코드 관련 이벤트는 제외하고, 사용자/증거/분석/보고서 중심으로 기록합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `logId` | Long | PK | 로그 ID |
| `actorId` | Long | FK, Not Null | 행위자 ID |
| `targetType` | LogTargetType | Not Null | 로그 대상 유형 |
| `targetId` | Long | Not Null | 로그 대상 ID (`targetType`에 대응하는 PK) |
| `actionType` | LogActionType | Not Null | 행위 유형 |
| `subjectHash` | String | Nullable | **이벤트 대상 파일**의 SHA-256 (원본/복사본/PDF 등) |
| `storagePathAtEvent` | String | Nullable | 이벤트 시점 대상 파일 경로 |
| `reason` | String | Nullable | 사유 |
| `clientIp` | String | Nullable | 접속 IP |
| `eventPayloadJson` | JSONB/Text | Nullable | 변경 내용/이벤트 상세 |
| `previousLogHash` | String | Nullable | **이전 로그 레코드**의 체인 해시 |
| `currentLogHash` | String | Not Null | **현재 로그 레코드**의 체인 해시 |
| `createdAt` | LocalDateTime | Not Null | 로그 생성 시각 |

### 해시 컬럼 구분

| 컬럼 | 의미 |
| :--- | :--- |
| `subjectHash` | 해당 이벤트가 다룬 **파일**의 SHA-256 (증거 무결성 추적용) |
| `previousLogHash` / `currentLogHash` | **로그 레코드** 간 Append-Only 체인 해시 (감사 추적용) |

> `subjectHash`와 `currentLogHash`는 서로 다른 개념입니다.  
> 예: `HASH_CREATED` 이벤트에서 `subjectHash = originalHashValue`, `currentLogHash`는 로그 레코드 자체의 체인 값입니다.

### FK 정책

- `evidenceId`, `analysisRequestId` 등 개별 nullable FK는 **사용하지 않습니다**.
- 대상 참조는 `targetType` + `targetId` 조합으로만 관리합니다. (중복 저장 방지)

### targetType

```text
USER
EVIDENCE
ANALYSIS_REQUEST
ANALYSIS_RESULT
REPORT
SYSTEM
```

`INVITE_CODE`는 사용하지 않습니다.

### actionType

```text
LOGIN
LOGOUT

SIGNUP_REQUEST
USER_APPROVED
USER_REJECTED
USER_SUSPENDED
USER_DELETED

EVIDENCE_UPLOADED
HASH_CREATED
METADATA_EXTRACTED
EVIDENCE_VIEWED

ANALYSIS_COPY_CREATED
ANALYSIS_COPY_VERIFIED
ANALYSIS_COPY_DELETED

ANALYSIS_REQUESTED
ANALYSIS_STARTED
ANALYSIS_COMPLETED
ANALYSIS_FAILED

REPORT_CREATED
REPORT_DOWNLOADED

ERROR_OCCURRED
```

초대코드 관련 actionType은 사용하지 않습니다.

---

## 4. Enum 정의

| Enum | 값 |
| :--- | :--- |
| `UserRole` | `ROLE_USER`, `ROLE_ADMIN` |
| `UserStatus` | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` |
| `OrgType` | `POLICE`, `PROSECUTION`, `NFS`, `PUBLIC_SECURITY`, `ETC` |
| `InviteStatus` | `ACTIVE`, `USED`, `EXPIRED`, `REVOKED` |
| `FileType` | `IMAGE`, `VIDEO`, `AUDIO` |
| `EvidenceStatus` | `UPLOADED`, `DELETED` |
| `CopyStatus` | `NONE`, `ACTIVE`, `DELETED` |
| `ExtractionStatus` | `SUCCESS`, `PARTIAL`, `FAILED` |
| `AnalysisStatus` | `QUEUED`, `ANALYZING`, `COMPLETED`, `FAILED` |
| `RiskLevel` | `LOW`, `MEDIUM`, `HIGH` |
| `LogTargetType` | `USER`, `EVIDENCE`, `ANALYSIS_REQUEST`, `ANALYSIS_RESULT`, `REPORT`, `SYSTEM` |
| `LogActionType` | `LOGIN`, `LOGOUT`, `SIGNUP_REQUEST`, `USER_APPROVED`, `USER_REJECTED`, `USER_SUSPENDED`, `USER_DELETED`, `EVIDENCE_UPLOADED`, `HASH_CREATED`, `METADATA_EXTRACTED`, `EVIDENCE_VIEWED`, `ANALYSIS_COPY_CREATED`, `ANALYSIS_COPY_VERIFIED`, `ANALYSIS_COPY_DELETED`, `ANALYSIS_REQUESTED`, `ANALYSIS_STARTED`, `ANALYSIS_COMPLETED`, `ANALYSIS_FAILED`, `REPORT_CREATED`, `REPORT_DOWNLOADED`, `ERROR_OCCURRED` |

---

## 5. 관계 설정

### 5.1 엔티티 FK 관계

```text
Users 1:N Evidences          (Evidences.uploaderId)
Users 1:N AnalysisRequests   (AnalysisRequests.requestedBy)
Users 1:N Reports            (Reports.createdBy)
Users 1:N InviteCodes        (InviteCodes.issuedBy)

InviteCodes 1:N Users        (Users.inviteCodeId)

Evidences 1:1 EvidenceMetadata
Evidences 1:N AnalysisRequests
Evidences 1:N Reports        (Reports.evidenceId — 조회 편의 비정규화)

AnalysisRequests 1:1 AnalysisResults
AnalysisResults 1:N AnalysisModuleResults
AnalysisResults 1:N Reports
Reports 1:1 ReportPublicationSnapshots
```

### 5.2 CustodyLogs — 다형 참조 (개별 FK 없음)

`CustodyLogs`는 `evidenceId`, `analysisRequestId` 등 **개별 nullable FK를 두지 않습니다**.  
대상 엔티티는 `targetType` + `targetId`로만 참조합니다.

| 컬럼 | 역할 |
| :--- | :--- |
| `actorId` | 행위자 (`Users.userId`, FK) |
| `targetType` + `targetId` | 이벤트 대상 (USER / EVIDENCE / ANALYSIS_REQUEST / ANALYSIS_RESULT / REPORT / SYSTEM) |

```text
CustodyLogs.actorId        → Users
CustodyLogs.targetType+targetId → Users | Evidences | AnalysisRequests | AnalysisResults | Reports (논리 참조)
```

JPA에서 `@ManyToOne Evidence` 같은 직접 연관을 CustodyLogs에 매핑하지 않습니다.

---

## 6. 최종 데이터 흐름

### 6.1 회원가입 및 승인 흐름

```text
1. 관리자가 1회용 초대코드 생성
2. 사용자가 초대코드로 회원가입 신청
3. Users.status = PENDING
4. 관리자 승인
5. Users.status = APPROVED
6. 관리자 승인 전까지 로그인 불가
7. 승인 후 로그인 가능
```

### 6.2 증거 업로드 및 메타데이터 흐름

```text
1. 사용자가 증거 파일 업로드
2. Evidences 저장 (status = UPLOADED)
3. 원본 SHA-256 → originalHashValue 저장
4. EvidenceMetadata 자동 추출 (extractionStatus 기록)
5. CustodyLogs: EVIDENCE_UPLOADED (subjectHash = originalHashValue)
6. CustodyLogs: HASH_CREATED (subjectHash = originalHashValue)
7. CustodyLogs: METADATA_EXTRACTED
```

### 6.3 AI 분석 흐름

```text
1. 사용자가 분석 요청 버튼 클릭
2. AnalysisRequests 생성 (status = QUEUED)
3. 원본 → 복사본 생성 (copyStoragePath, copyStatus = ACTIVE)
4. 복사본 SHA-256 → copyHashValue 저장
5. CustodyLogs: ANALYSIS_COPY_CREATED (subjectHash = copyHashValue)
6. (byte copy인 경우) originalHashValue == copyHashValue 검증
7. CustodyLogs: ANALYSIS_COPY_VERIFIED (선택)
8. AI 서버 분석 시작 (AnalysisRequests.status = ANALYZING)
9. AI 분석 완료 → AnalysisResults, AnalysisModuleResults 저장
10. AnalysisRequests.status = COMPLETED
11. CustodyLogs: ANALYSIS_COMPLETED
12. 복사본 삭제 → copyStatus = DELETED, copyDeletedAt 기록
13. CustodyLogs: ANALYSIS_COPY_DELETED (subjectHash = copyHashValue 유지)

※ 분석 FAILED 후 재시도: 기존 row 수정 없이 새 AnalysisRequests row 생성 (§2.7, §7)
```

### 6.4 PDF 보고서 흐름

```text
1. 사용자가 PDF 다운로드 클릭
2. 분석 결과 기반 PDF 생성
3. PDF 파일 S3 저장
4. PDF SHA-256 → Reports.reportHash 저장
5. Reports를 ISSUED 상태로 저장
6. 발행 입력값·공개 요약·산출물 목록·표시 정책을 ReportPublicationSnapshots에 1회 저장
7. CustodyLogs: REPORT_CREATED (subjectHash = reportHash, targetType = REPORT)
8. 다운로드 시 CustodyLogs: REPORT_DOWNLOADED (subjectHash = reportHash)
```

---

## 7. 재분석·재시도 정책

MVP에서는 **재분석(완료 후 재실행)** 을 허용하지 않습니다.  
**실패 재시도**만 허용하며, 재시도는 **새 `AnalysisRequests` row 생성**으로 처리합니다.

```text
COMPLETED 존재 → 분석 요청 버튼 비활성화 (새 row 생성 불가)
FAILED 존재    → 재시도 버튼 → 새 AnalysisRequests row 생성
기존 FAILED row → 수정하지 않음 (이력 보존)
재분석 토글/UI → 제공하지 않음
```

### 재분석을 허용하지 않는 이유

완료 후 재실행 시 결과가 달라질 수 있어 보고서 기준점이 모호해집니다.

```text
1차 분석: 위험도 82
2차 분석: 위험도 61  → MVP에서 허용하지 않음
```

---

## 8. 분석 건수 통계 (RQ-DSH-043)

대시보드 stats는 별도 컬럼 없이 기존 테이블을 집계합니다. **MVP는 영상(VIDEO) 전용**이며, API는 미디어 유형별 3카드가 아니라 **4카드**를 반환합니다.

### 8.1 통계 항목 (`GET /api/v1/evidences/stats`)

| 필드 | 기준 |
| :--- | :--- |
| `totalAnalysisCount` | 해당 사용자의 분석 요청 전체 건수 |
| `deepfakeDetectedCount` | COMPLETED + 위험도 HIGH/MEDIUM |
| `completedCount` | COMPLETED 건수 |
| `inProgressCount` | QUEUED + ANALYZING 건수 |

### 8.2 집계 기준

분석 건수는 단순 업로드 건수가 아니라 **AI 분석이 완료된 건수**를 기준으로 합니다.

```text
업로드만 된 파일 = 분석 건수에 포함하지 않음
분석 요청 후 COMPLETED 된 파일 = 분석 건수에 포함
FAILED 된 분석 요청 = 실패 건수로 별도 집계 가능
```

### 8.3 SQL 집계 예시 (완료 건수)

```sql
SELECT COUNT(*)
FROM analysis_requests ar
WHERE ar.requested_by = :uploaderId
  AND ar.status = 'COMPLETED';
```

### 8.4 대시보드 표시 예시

```json
{
  "totalAnalysisCount": 25,
  "deepfakeDetectedCount": 3,
  "completedCount": 20,
  "inProgressCount": 2
}
```

### 8.5 설계 판단

분석 건수는 기존 데이터에서 계산 가능한 값이므로 별도 저장 컬럼을 두지 않습니다. 대시보드 조회 시 COUNT로 계산하는 것이 더 안전합니다.

### 8.6 관리자 분석 통계 (`GET /api/v1/admin/dashboard/analysis-stats`, RQ-ADMIN-150)

사용자 대시보드(§8.1)와 동일한 **COMPLETED 기준**을 전체 시스템에 적용합니다.

| 필드 | 집계 범위 | 기준 |
| :--- | :--- | :--- |
| `weeklyTotalCount` | 이번 주(월~일) | `status = COMPLETED` AND `completedAt` 해당 주 |
| `deepfakeDetectionRate` | 이번 주 완료 건 | `riskLevel IN (MEDIUM, HIGH)` / 완료 건수 × 100 |
| `averageAnalysisMinutes` | 이번 주 완료 건 | `AVG(completedAt - COALESCE(startedAt, requestedAt))` |
| `weeklyPoints[].requestedCount` | 일별 | `requestedAt` 해당 일 |
| `weeklyPoints[].completedCount` | 일별 | `completedAt` 해당 일, COMPLETED |
| `riskDistribution.*` | 전체 완료 분석 | `riskScore` 0~49 / 50~79 / 80~100 |

- 대상: **모든 사용자** 업로드·미삭제(`deletedAt IS NULL`) 증거
- 업로드만 되고 분석이 완료되지 않은 건은 분석 건수에 **포함하지 않음** (§8.2 동일)

---

## 9. 발표/문서용 요약 문장

ForenShield AI는 초대코드 기반 내부 사용자 계정 신청 구조를 사용합니다. 가입 시 `role`은 항상 `ROLE_USER`이며, 관리자 계정은 별도로 생성합니다. 가입 신청 후 `PENDING` 상태가 되고 관리자 승인 전까지 로그인이 제한됩니다.

증거 파일은 관리자 승인 없이 업로드 즉시 `UPLOADED` 상태로 저장되며, SHA-256 해시와 메타데이터를 자동 생성합니다. AI 분석은 사용자 요청 시에만 실행되고, 완료 후 재분석은 불허하며 실패 시에만 새 분석 요청 row로 재시도합니다.

AI 분석 결과는 PostgreSQL에 저장하고, PDF 보고서는 S3에 저장합니다. CoC 로그는 `subjectHash`(파일 해시)와 `currentLogHash`(로그 체인)를 구분하여 기록하며, 초대코드 관련 이벤트는 CoC 범위에서 제외합니다.
