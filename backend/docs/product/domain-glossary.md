# 도메인 용어집 (Glossary)

> **대상:** FE · BE · AI · INF · AI 에이전트  
> API·DB·UI에서 **동일한 단어 = 동일한 의미**를 유지합니다.

---

## 1. 핵심 엔티티

| 용어 (EN) | 한글 | 설명 | DB / API |
| :--- | :--- | :--- | :--- |
| **User** | 사용자 | 로그인 계정. Admin 승인 필요 | `Users` |
| **Evidence** | 증거 | 업로드된 미디어 파일 1건 | `Evidences.evidenceId` |
| **Case** | 사건 | 여러 Evidence를 묶는 논리 단위. MVP는 **사건명(caseName)** 문자열로 식별 | `Evidences.caseName`, `GET /api/v1/cases/{caseId}` |
| **Analysis Request** | 분석 요청 | Evidence 1건당 분석 Job 1회(재시도 시 새 row) | `AnalysisRequests` |
| **Analysis Result** | 분석 결과 | AI 종합 결과 (riskScore, riskLevel) | `AnalysisResults` |
| **Module Result** | 모듈별 결과 | Lip-sync, deepfake 등 세부 | `AnalysisModuleResults` |
| **Custody Log (CoC)** | 보관연속성 로그 | 증거·계정 행위 감사 기록 (해시 체인) | `CustodyLogs` |
| **Invite Code** | 초대코드 | 회원가입 1회용 | `InviteCodes` |

---

## 2. 상태 Enum (API·DB 동일 문자열)

### Users.status

| 값 | 의미 |
| :--- | :--- |
| `PENDING` | 가입 승인 대기 |
| `APPROVED` | 로그인 가능 |
| `REJECTED` | 가입 반려 |
| `SUSPENDED` | 계정 정지 |

### Users.role

| 값 | 의미 |
| :--- | :--- |
| `ROLE_USER` | 일반 사용자 |
| `ROLE_ADMIN` | 관리자 (`/api/v1/admin/**`) |

### AnalysisRequests.status

| 값 | 의미 | FE polling 표시 |
| :--- | :--- | :--- |
| `QUEUED` | 큐 대기 | `PENDING` |
| `ANALYZING` | AI 처리 중 | `PROCESSING` |
| `COMPLETED` | 완료 | `COMPLETED` |
| `FAILED` | 실패 | `FAILED` |

> **주의:** 분석 상태는 `Evidences`에 두지 않습니다. [ERD](../database/erd.md)

### RiskLevel (AnalysisResults)

| 값 | 의미 |
| :--- | :--- |
| `LOW` | 정상/낮은 위험 |
| `MEDIUM` | 의심 |
| `HIGH` | 고위험·딥페이크 가능성 높음 |

대시보드 `deepfakeDetectedCount`: COMPLETED + `HIGH`/`MEDIUM`

### FileType

> **MVP 업로드:** `VIDEO`만 허용 (MP4, MOV). DB enum에는 `IMAGE`/`AUDIO`가 남아 있으나 신규 업로드·AI 큐는 영상 전용.

| 값 | 확장자 (예) | MVP |
| :--- | :--- | :---: |
| `VIDEO` | mp4, mov | ✅ |
| `IMAGE` | jpg, png | — (미지원) |
| `AUDIO` | wav, mp3, m4a | — (미지원) |

---

## 3. S3 경로 용어

| 경로 | 설명 | 수정 |
| :--- | :--- | :--- |
| `original/` | 업로드 직후 원본 (WORM) | **불가** |
| `copy/` | AI 분석용 사본 | AI Read-only |
| `reports/` | PDF 등 결과물 | BE Write |

상세: [../integrations/s3.md](../integrations/s3.md)

---

## 4. ID 체계

| ID | 형식 | 예 |
| :--- | :--- | :--- |
| **RQ** | `RQ-{영역}-{번호}` | `RQ-LOGIN-020` |
| **FN** | `FN-{영역}-{번호}-{FE\|BE\|AI\|INF}` | `FN-REQ-049-BE` |

영역 코드: `COM`, `LOGIN`, `SIGNUP`, `DSH`, `REQ`, `DTL`, `CMP`, `HIS`, `MY`, `ADMIN`, `SEC`, `PER`, `NFR` …

→ [../requirements/overview.md](../requirements/overview.md)

---

## 5. API 관례 용어

| 용어 | 설명 |
| :--- | :--- |
| **errorCode** | FE 분기용 `UPPER_SNAKE_CASE` (예: `ACCOUNT_PENDING`) |
| **StandardErrorResponse** | `{ success, errorCode, message, details? }` |
| **Legacy alias** | 과거 `/api/evidences` = `/api/v1/evidences` 병행 — **2026-06 제거**, 로그인 `/api/auth/login`만 legacy |

→ [../guides/implementation-standards.md](../guides/implementation-standards.md)

---

## 6. 자주 혼동하는 것

| 잘못 | 올바름 |
| :--- | :--- |
| Evidence에 `ANALYZING` 저장 | `AnalysisRequests.status` |
| 가입 시 `ROLE_ADMIN` 선택 | 서버에서 항상 `ROLE_USER` |
| AI가 `original/` 수정 | `copy/`만 사용 |
| 로그인 PENDING → HTTP 403 | HTTP 401 + `ACCOUNT_PENDING` |
| Admin 목록 `items` / `total` | `content` / `totalElements` |
