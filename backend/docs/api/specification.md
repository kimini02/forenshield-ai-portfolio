# ForenShield API Specification

> **버전:** v1.9 (2026-07-14 — Controller·FE 연동 전면 동기화)  
> **기준:** `기능명세서_최종.xlsx` · `요구사항명세서_최종 (1).xlsx` · 실제 Controller  
> **관련:** [convention.md](./convention.md) · [../guides/implementation-standards.md](../guides/implementation-standards.md) · [signup.md](./signup.md)  
> **읽는 법:** Gap·세부 계약은 §0~§2 · **현재 엔드포인트 한눈 목록은 §6**

---

## 0. 기능명세서 vs 현재 코드 — Gap 분석

### 0.1 한 줄 요약

| 구분 | 판정 |
| :--- | :--- |
| **인증·가입·마이페이지·관리자 CRUD** | ✅ 일치 (`/api/v1`) |
| **증거·분석 API** | ✅ v1 prefix + stats/analyze/auth **통일 완료** (`/api/evidences` legacy alias 제거) |
| **에러 JSON·예외 Handler** | ✅ `StandardErrorResponse` 단일 |
| **Admin 페이지네이션** | ✅ `content` / `totalElements` |
| **비교검증·PDF·알림·설정·블록체인** | ✅ **구현 완료** (INF 블록체인 http · Manifest **운영 CA Secret** 대기) |

> **FE 연동:** [§2 현재 구현 정본](#2-현재-구현-정본-controller-기준) 사용  
> **AI 에이전트:** [../AGENTS.md](../AGENTS.md) → 본 문서 §2

---

### 0.2 경로 불일치 (Prefix)

기능명세서 FE 시트「호출 API」와 코드 Controller `@RequestMapping` 비교:

| 기능 | 기능명세서 (목표) | 현재 코드 | 상태 |
| :--- | :--- | :--- | :--- |
| 대시보드 통계 | `GET /api/v1/evidences/stats` | 동일 | ✅ |
| 파일 업로드 | `POST /api/v1/evidences/upload` | 동일 | ✅ |
| 분석 요청 | `POST /api/v1/evidences/analyze` | `{ evidenceId }` + `{ evidenceIds }` | ✅ |
| 로그인 PENDING | HTTP 401 + `ACCOUNT_PENDING` | HTTP 401 + `errorCode` | ✅ |
| 가입 에러 JSON | `errorCode` + `success` | `StandardErrorResponse` | ✅ |
| 로그인 | `POST /api/auth/login` | `POST /api/auth/login` | ✅ |
| 가입 | `POST /api/v1/auth/signup` | `POST /api/v1/auth/signup` | ✅ |
| 초대코드 검증 | `POST /api/v1/invite-codes/validate` | `POST /api/v1/invite-codes/validate` | ✅ |
| 분석 이력 | `GET /api/v1/mypage/analysis-history` | 동일 (+ alias `GET /api/v1/cases/me`) | ✅ |
| 사건/상세 | `GET /api/v1/cases/{caseId}` | 동일 | ✅ |
| 증거 상세 | *(명세: case API에 포함)* | `GET /api/evidences/{evidenceId}/detail` | ⚠️ 추가 엔드포인트 |
| 분석 상태 | *(명세에 경로 없음)* | `GET /api/evidences/{evidenceId}/analysis-status` | ➕ 코드만 |
| 프로필 | `GET/PATCH /api/v1/users/me` | 동일 | ✅ |
| 관리자 | `GET/POST/PATCH /api/v1/admin/*` | 세부 경로 구현됨 (§2.5) | ✅ |

---

### 0.3 요청·응답 Body 불일치

| API | 기능명세서 | 현재 코드 | Gap |
| :--- | :--- | :--- | :--- |
| **분석 요청** | `{ "evidenceId": 1 }` (단건) | `{ evidenceId }` 또는 `{ evidenceIds, caseName? }` | ✅ |
| **대시보드 stats** | 4카드 (RQ-DSH-043) | `totalAnalysisCount`, `deepfakeDetectedCount`, … | ✅ |
| **상세 조회** | `GET /api/v1/cases/{caseId}` + evidence detail | `CaseDetailResponse` + `EvidenceDetailResponse` | 🟡 분할 유지 |
| **로그인 실패 (PENDING)** | HTTP 401 + `ACCOUNT_PENDING` | HTTP 401 + `errorCode` | ✅ |
| **에러 JSON** | `errorCode` + `success` | `StandardErrorResponse` 전 API | ✅ |

---

### 0.4 기능명세서·RQ 기준 — 잔여 Gap

| 영역 | RQ (예) | API | 상태 |
| :--- | :--- | :--- | :---: |
| PDF 리포트 | DTL-084~087 | `GET .../reports/pdf`, `.../verify` | ✅ |
| 비교 검증 | CMP-091~104 | `POST /api/v1/compare/verify` 등 | ✅ |
| 알림 | COM-015~016 | `GET/PATCH /api/v1/notifications` | ✅ |
| 환경 설정 | COM-009 | `GET/PATCH /api/v1/users/me/settings` | ✅ |
| Recovery Score | DTL-071~072 | `detail.integrityInfo` | ✅ |
| CoC 체인 검증 | HIS-107 | `GET .../coc/verify` | ✅ |
| X.509 사본 서명 | REQ-050, DTL-075~076 | 분석 copy Manifest + **플랫폼 PKCS#8** 서명 | ✅ |
| 블록체인 앵커 | REQ-052, DTL-078 | `GET .../blockchain` | 🟡 INF URL 대기 |
| 대시보드 7일·최근 | DSH-044~045 | `stats/trend`, `stats/recent` | ✅ |
| 대시보드 stats 캐시 | PER-155 | `GET .../stats` 30초 TTL · 분석 완료 시 invalidate | 🟡 실측 대기 |
| 분석 큐 지연 | PER-154, 3.1.4 | `analysis-status` `queuePosition`/`queueDepth` · stale `ANALYSIS_TIMEOUT` | ✅ |
| Servlet 업로드 한도 초과 | PER-154 | HTTP **413** + `FILE_TOO_LARGE` | ✅ |
| Admin Merkle 수동 앵커 | REQ-052, 2.10.2 | `POST /api/v1/admin/blockchain/merkle/anchor` | ✅ |
| PDF CoC 이벤트 | DTL-084~087 | `REPORT_CREATED` · `REPORT_DOWNLOADED` custody | ✅ |
| 로그아웃 | COM-011 | 클라이언트 sessionStorage 삭제 (서버 API 불필요) | — |


---

### 0.5 코드에만 있음 (명세 FE 시트에 경로 미기재)

| Method | Path | 용도 |
| :--- | :--- | :--- |
| GET | `/api/v1/auth/username/check` | 아이디 중복 확인 ([signup.md](./signup.md)) |
| GET | `/api/v1/organizations/departments` | 부서 자동완성 |
| GET | `/api/v1/cases/me` | 분석 이력 alias |
| GET | `/api/v1/evidences/{evidenceId}/detail` | 증거 상세 |
| GET | `/api/v1/evidences/{evidenceId}/analysis-status` | 분석 진행률 polling |
| DELETE | `/api/v1/evidences/{evidenceId}` | 업로드 취소 |
| DELETE | `/api/v1/evidences/{evidenceId}/reset` | 증거+분석 초기화 |
| DELETE | `/api/v1/evidences/{evidenceId}/analysis` | 분석 중단 |
| GET | `/api/v1/evidences/{evidenceId}/coc/verify` | CoC 해시 체인 검증 (RQ-HIS-107) |
| POST | `/api/v1/evidences/{evidenceId}/access-events` | 증거 열람·캡처 시도 감사 로그 |
| GET | `/api/v1/evidences/{evidenceId}/reports/pdf` | PDF 리포트 |
| GET/DELETE | `/api/v1/admin/evidences/**` | 관리자 증거 관리 |
| DELETE | `/api/v1/admin/users/{userId}` | 관리자 계정 삭제 |
| GET | `/api/v1/admin/reviewers` | 검토자 목록 |
| POST/PATCH | `/api/v1/cases/review-*` · `/reviewer` | 검토 요청·배정·결정 (§0.12) |
| GET | `/api/v1/compare/originals` | 비교용 원본 증거 목록 (RQ-CMP-091) |
| GET | `/api/v1/compare/originals/{evidenceId}` | 원본 파일 기본정보 (SK-954) |
| GET | `/api/v1/compare/{compareId}/candidate` | 대조본 파일 기본정보 (SK-955) |

→ **유지 권장** (실사용·테스트 존재). 명세 Excel FE 시트에 경로 추가 반영 필요. 전체 목록은 [§6](#6-quick-reference--현재-구현-전체-controller-기준-2026-07-14).

---

### 0.6 Gap 해소 우선순위 (팀 합의안)

| 순위 | 작업 | 담당 |
| :---: | :--- | :--- |
| 1~4 | Evidence v1 · stats · analyze · auth errorCode | ✅ 완료 |
| 5 | PDF / Compare / Notifications / Settings / Blockchain | ✅ BE |
| 6 | 기능명세서 Excel FE「호출 API」열 갱신 | PM/문서 |

---

### 0.7 FE 연동 범위 vs BE 전용 API (2026-07-14 갱신)

현재 **Next.js FE**(`frontend-deepfake`) 연동 여부. 전체 목록은 [§6](#6-quick-reference--현재-구현-전체).

| API | FE 사용 | 비고 |
| :--- | :---: | :--- |
| `POST /api/v1/evidences/upload` | ✅ | |
| `POST /api/v1/evidences/analyze` | ✅ | |
| `GET /api/v1/evidences/{id}/detail` | ✅ | step-up 헤더 |
| `GET /api/v1/evidences/stats*` | ✅ | 분석관 대시보드 (`/main`) |
| `GET /api/v1/evidences/{id}/analysis-status` | ✅ | polling |
| `GET /api/v1/evidences/{id}/readiness` · `POST …/readiness-check` | ✅ | 화질 soft-gate |
| `DELETE /api/v1/evidences/{id}` · `/reset` · `/analysis` | ✅ | 업로드/분석 취소·리셋 |
| `GET /api/v1/evidences/{id}/reports/pdf` | ✅ | step-up · 미리보기 query |
| `GET /api/v1/evidences/{id}/integrity/verify` | ✅ | `lib/api/evidence-verification.ts` |
| `GET /api/v1/evidences/{id}/blockchain` | ✅ | `lib/api/blockchain.ts` |
| `GET /api/v1/evidences/{id}/coc/verify` | ❌ | BE 전용 · 상세 `cocLogs`로 대체 |
| `GET /api/v1/evidences/dashboard/intro` | ❌ | BE 전용 · FE 미사용 |
| `GET /api/v1/evidences/{id}/reports/verify` | ❌ | BE 전용 · 공개 검증은 `/public/reports` |
| `GET/PATCH /api/v1/notifications*` | ✅ | `lib/api/notifications.ts` |
| `GET/PATCH /api/v1/users/me/settings` | ✅ | `lib/api/user-settings.ts` |
| `POST /api/v1/cases/review-request` · `review-decision` | ✅ | 검토 플로우 |
| `PATCH /api/v1/cases/reviewer` · `/{caseId}/reviewer` | ✅ | 관리자 배정 (`/admin/reviews`) |
| `GET /api/v1/admin/reviewers` | ✅ | |
| `GET /api/v1/mypage/analysis-history` | ✅ | 검토자 대시보드도 동일 API 재사용 |
| `GET /api/v1/reports` · public report APIs | ✅ | `/reports`, `/verify` |
| HLS `…/hls/master|key|segments` | ✅ | `streamToken` + step-up(key) |

> **리팩터링 원칙:** FE가 쓰는 경로·JSON 필드는 변경하지 않습니다. ❌ API는 **BE 전용**으로 유지하며 삭제하지 않습니다.

**Legacy 경로:** `/api/evidences/*` alias는 **제거됨** (2026-06). 신규·FE는 `/api/v1/evidences/*`만 사용. 로그인 `POST /api/auth/login` legacy는 유지.

### 0.8 v2 사건 중심 증거 워크플로우 API (2026-07)

FE `lib/api/case-workflow.ts` v2 플로우 연동. 기존 업로드·분석 API는 §2.3 유지.

| API | FE 함수 | 설명 |
| :--- | :--- | :--- |
| `PATCH /api/v1/evidences/{evidenceId}/exclude` | `markEvidenceExcluded` | 사용 제외 (`lifecycleStatus=EXCLUDED`) |
| `POST /api/v1/evidences/{evidenceId}/replace` | `replaceEvidenceInCase` | 대체 증거 업로드 (`multipart`: `file`, optional `reason`) |
| `PATCH /api/v1/cases/representative?caseKey=` | `setRepresentativeEvidence` | 대표 증거 지정 |
| `PATCH /api/v1/evidences/{evidenceId}/role` | `setEvidenceRole` | PRIMARY / SUPPLEMENT |

**응답 확장 필드** (하위 호환 — optional):

| DTO | 추가 필드 |
| :--- | :--- |
| `CaseDetailResponse` | `representativeEvidenceId` |
| `CaseEvidenceSummaryDto` / `EvidenceInfoDto` | `displayLabel`, `originalFileName`, `lifecycleStatus`, `role`, `replacementEvidenceId`, `excludedReason` |
| `CaseSummaryResponse` (mypage) | `representativeEvidenceId`, `representativeEvidenceLabel` |

`createCase()`는 `POST /api/v1/cases`로 서버에 `case_profiles`를 먼저 생성하고, 이후 `POST /evidences/upload?caseName=`으로 증거를 추가합니다. (레거시: 첫 업로드만으로도 사건 생성 가능)

### 0.9 사건명 변경 · 보고서 목록 API (2026-07)

FE v2 사건 편집 모달·`/reports` 페이지 연동용.

| API | FE (예상) | 설명 |
| :--- | :--- | :--- |
| `POST /api/v1/cases` | `createCase()` | Body: `{ "caseName": "사건명" }` — 증거 없이 `case_profiles`만 생성 |
| `PATCH /api/v1/cases?caseKey=` | 사건 편집 저장 | Body: `{ "caseName": "새 사건명" }` — 해당 사건 증거 전체 `caseName`/`caseNumber` 일괄 변경 |
| `GET /api/v1/reports` | 보고서 목록 | `page`, `size` — 사용자가 생성한 분석·비교 PDF 목록 |

**`POST /api/v1/cases`**

- **Auth:** INVESTIGATOR · ORG_ADMIN (JWT)
- **Body:** `{ "caseName": string }`
- **Response 200:** `CaseDetailResponse` (`evidences: []`, `status: "PENDING"`)
- **Errors:** `DUPLICATE_CASE_NAME` (409), `INVALID_REQUEST` (400), `FORBIDDEN` (403)
- 동일 uploader + `case_key` 중복 시 409 (rename과 동일 규칙)
- `GET /api/v1/mypage/analysis-history`에 증거 0건 사건도 포함

**`PATCH /api/v1/cases?caseKey=`**

- **Response 200:** `CaseDetailResponse` (변경 후 `caseKey`로 조회한 결과)
- **Errors:** `CASE_NOT_FOUND` (404), `DUPLICATE_CASE_NAME` (409), `INVALID_REQUEST` (400)
- CoC: 대표 증거 1건에 `CASE_RENAMED` 기록 (해시·스토리지 경로 불변)

**`GET /api/v1/reports`**

**Response 200:** `ReportListPageResponse`

```json
{
  "content": [
    {
      "reportId": 1,
      "reportType": "ANALYSIS",
      "evidenceId": 101,
      "compareId": null,
      "caseId": "사건-A",
      "caseName": "사건-A",
      "reportFileName": "analysis-report-101.pdf",
      "verdictLabel": "위험",
      "createdAt": "2026-07-01T12:00:00Z",
      "reportHash": "abc...",
      "downloadPath": "/api/v1/evidences/101/reports/pdf"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

| 필드 | 설명 |
| :--- | :--- |
| `reportType` | `ANALYSIS` (분석 PDF) · `COMPARE` (비교 PDF) |
| `verdictLabel` | 분석: `적합`/`주의`/`위험` · 비교: `원본 일치`/`위변조 의심`/`판정 불가` |
| `downloadPath` | 기존 PDF 다운로드 API 경로 (FE가 그대로 호출) |

PDF **생성**은 기존 `GET /api/v1/evidences/{id}/reports/pdf`, `GET /api/v1/compare/{compareId}/reports/pdf` 유지.

### 0.10 v2 응답 보강 — 분석 진행률 · 미디어 URL · 업로드 라벨 (2026-07)

FE 사건 상세·증거 상세·업로드 패널 연동용 optional 필드.

| API | 추가/채움 필드 | 설명 |
| :--- | :--- | :--- |
| `GET /api/v1/cases?caseKey=` | `evidences[].analysisProgress` | 최신 분석 요청 `progressPercent` (완료 시 100, 미요청 0) |
| `GET /api/v1/cases?caseKey=` | `evidences[].hlsStatus` | HLS 패키징 상태 (`PENDING`/`PACKAGING`/`READY`/`FAILED`) — presigned URL 제거 |
| `GET /api/v1/evidences/{id}/detail` | `hlsPlayback` | HLS 재생 메타 (`manifestPath`, `hlsStatus`, `streamToken`, `expiresIn`) — step-up 성공 후 streamToken 발급 (§0.14) |
| `POST /api/v1/evidences/upload` | `displayLabel` | 사건 내 순번 라벨 (`증거 1`, `증거 2`, …) — DB에도 저장 |

### 0.11 증거 열람·캡처 감사 로그 API (2026-07)

FE `ProtectedEvidencePlayer` / `EvidenceWatermarkOverlay` 연동. 영상 재생·PrintScreen 감지 시 CoC에 접근 이벤트를 기록합니다.

| API | FE (예상) | 설명 |
| :--- | :--- | :--- |
| `POST /api/v1/evidences/{evidenceId}/access-events` | `recordEvidenceAccessEvent` | Body: `eventType` (`VIEW` \| `CAPTURE_ATTEMPT`), optional `caseKey`, `source` — **204** |

**CoC actionType:** `EVIDENCE_VIEWED`, `EVIDENCE_CAPTURE_ATTEMPTED`  
**Refs:** RQ-REQ-051 (감사 로그 해시 체이닝)

### 0.14 HLS 암호화 스트림 API (2026-07)

S3 presigned URL 대신 BE 프록시 HLS 재생. FE는 `hls.js` + `streamToken` 쿼리로 manifest/key/segment 요청.

| API | Auth | 설명 |
| :--- | :--- | :--- |
| `GET /api/v1/evidences/{id}/hls/master.m3u8?streamToken=` | JWT + streamToken | master manifest (키·세그먼트 URI는 BE 경로로 재작성) |
| `GET /api/v1/evidences/{id}/hls/key?streamToken=` | JWT + streamToken + **X-Step-Up-Token** | AES-128 키 16바이트 |
| `GET /api/v1/evidences/{id}/hls/segments/{file}?streamToken=` | JWT + streamToken | 암호화 `.ts` 세그먼트 |

**`hlsPlayback` (detail 응답):** `manifestPath`, `hlsStatus`, `streamToken`, `expiresIn`(초, 기본 900)  
**Errors:** `STREAM_TOKEN_REQUIRED` (403), `STEP_UP_REQUIRED` (403), `INVALID_STREAM_TOKEN` (403)

### 0.12 RBAC · 검토 워크플로우 API (2026-07)

FE `lib/permissions.ts` · 검토 배정(`/admin/reviews`) · 사건 상세 검토 플로우 연동.

**역할 (`UserRole`)**

| BE 값 | FE 정규화 | 설명 |
| :--- | :--- | :--- |
| `ROLE_INVESTIGATOR` / `ROLE_USER` | `INVESTIGATOR` | 본인 사건 업로드·분석·검토 요청 |
| `ROLE_REVIEWER` | `REVIEWER` | 배정된 사건만 조회·검토 결정 |
| `ROLE_ORG_ADMIN` / `ROLE_ADMIN` | `ORG_ADMIN` | 기관 사건·사용자·검토자 배정 |

**`GET /api/v1/users/me` 확장 (`UserProfileResponse`)**

| 필드 | 설명 |
| :--- | :--- |
| `organizationType` | `POLICE` · `PROSECUTION` · … |
| `organizationId` | 예: `org-police` |
| `organizationName` | 예: `경찰기관` |
| `role` | `ROLE_INVESTIGATOR` · `ROLE_REVIEWER` · `ROLE_ORG_ADMIN` 등 |

**`GET /api/v1/mypage/analysis-history` — 역할별 필터 + `CaseSummaryResponse` RBAC 필드**

| 필드 | 설명 |
| :--- | :--- |
| `organizationId` | 기관 식별자 |
| `department` | 담당 부서 |
| `createdBy` | 사건 생성자 userId (string) |
| `assigneeId` | 담당자 userId |
| `reviewerId` | 배정 검토자 userId |
| `reviewStatus` | `NONE` · `REVIEW_REQUESTED` · `REVIEW_ASSIGNED` · `REPORT_APPROVED` · `REVIEW_SUPPLEMENT_REQUESTED` |
| `aiResult` | `낮음` · `검토 필요` · `위험` |
| `reviewRequestedAt` | ISO-8601 UTC |

- **ORG_ADMIN:** 동일 `organizationType` 사건 전체
- **INVESTIGATOR:** 본인 업로드 사건
- **REVIEWER:** `reviewerId`가 본인인 사건만

**사건 검토 API (`CaseController`)**

| Method | Path | Auth | Body | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/cases/review-request?caseKey=` | User | `{ "memo"?: string }` | 분석 완료 사건 검토 요청 → `REVIEW_REQUESTED` |
| `PATCH` | `/api/v1/cases/reviewer?caseKey=` | ORG_ADMIN | `{ "reviewerId": string }` | 검토자 배정 → `REVIEW_ASSIGNED` |
| `POST` | `/api/v1/cases/review-decision?caseKey=` | REVIEWER/ORG_ADMIN | `{ "decision": "APPROVED"\|"REVISION", "memo"?: string }` | 승인 또는 재검토 요청 |

**`GET /api/v1/cases?caseKey=` 확장 (`CaseDetailResponse`)**

| 필드 | 설명 |
| :--- | :--- |
| `createdBy` | 사건 소유자 userId |
| `assigneeId` | 담당자 userId |
| `reviewerId` | 검토자 userId |
| `reviewStatus` | 검토 상태 enum name |

검토자·기관관리자는 배정/소속 사건에 대해 `GET /cases` 접근 가능.

**증거 단건 조회 RBAC (`EvidenceAccessService.requireReadable`)**

| API 예시 | 소유자 | 배정 검토자 | 기관 관리자 |
| :--- | :---: | :---: | :---: |
| `GET /api/v1/evidences/{id}/detail` | ✅ | ✅ (배정 사건) | ✅ (동일 기관) |
| `GET /api/v1/evidences/{id}/analysis-status` | ✅ | ✅ | ✅ |
| `GET /api/v1/evidences/{id}/integrity/verify` | ✅ | ✅ | ✅ |
| `GET /api/v1/evidences/{id}/reports/pdf` | ✅ | ✅ | ✅ |
| 업로드·분석·제외·삭제 등 변경 API | ✅ | ❌ | ❌* |

\* 기관 관리자도 증거 변경 API는 소유자 전용 (`requireOwned` 유지)

**관리자 검토자 목록**

| Method | Path | Query | Response |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/reviewers` | `department?` | `{ "reviewers": [ { "id", "name", "department", "organizationId", "organizationName", "organizationType" } ] }` |

- `ROLE_ORG_ADMIN`: 동일 `organizationType`의 승인된 `ROLE_REVIEWER`만
- `ROLE_ADMIN`: 전체 기관 승인 검토자 (운영용)

**관리자 사용자 API 보강**

| API | 추가 |
| :--- | :--- |
| `GET /api/v1/admin/users` | Query `role` (예: `REVIEWER`) · `AdminUserItemResponse`에 `organizationType`, `organizationId`, `organizationName` |
| `POST /api/v1/admin/users/{id}/approve` | Body `{ "role": "INVESTIGATOR\|REVIEWER\|ORG_ADMIN" }` (선택) |

**DB:** `005_case_review_rbac.postgresql.sql` (`case_profiles` 검토 메타데이터)

---

## 1. 공통

| 항목 | 값 |
| :--- | :--- |
| Base URL (표준) | `/api/v1` |
| Legacy | `/api/auth/login` only (`/api/evidences/*` alias **removed** 2026-06) |
| 인증 | `Authorization: Bearer {JWT}` |
| Public | login, signup, username/check, invite validate, departments |
| Admin | `/api/v1/admin/**` + `ROLE_ADMIN` |
| 에러 형식 | [../guides/implementation-standards.md](../guides/implementation-standards.md) (`StandardErrorResponse`) |

---

## 2. 현재 구현 정본 (Controller 기준)

> **FE·BE는 아래 경로를 당분간 Source of Truth로 사용합니다.**

### 2.1 인증 · 가입

#### POST `/api/auth/login` — 로그인

| | |
|---|---|
| **RQ** | RQ-LOGIN-020 |
| **Auth** | Public |

**Request**

```json
{ "loginId": "hong123", "password": "Password123!" }
```

**Response 200**

```json
{
  "success": true,
  "token": "<JWT>",
  "userId": 1,
  "loginId": "hong123",
  "name": "홍길동",
  "role": "ROLE_USER"
}
```

**Errors:** 401 credentials / 403 account status (⚠️ 명세는 401+errorCode)

---

#### POST `/api/v1/auth/step-up/verify` — Step-up 재인증

| | |
|---|---|
| **Auth** | User (Bearer JWT 필수) |
| **용도** | 민감 정보(증거 상세) 조회 전 비밀번호 재확인 |

**Request**

```json
{ "password": "Password123!" }
```

**Response 200**

```json
{
  "success": true,
  "stepUpToken": "<opaque-uuid>",
  "expiresIn": 900000
}
```

- `expiresIn`: ms 단위 TTL (기본 15분 = 900000)
- 성공 시 CoC에 `STEP_UP_VERIFIED` 기록 (`targetType: USER`)
- Rate limit: **사용자(loginId)당 1분 5회** (brute force 방지)
- 이후 `GET /api/v1/evidences/{id}/detail` 호출 시 `X-Step-Up-Token: <stepUpToken>` 헤더 필수

**Errors:** 401 `INVALID_STEP_UP_PASSWORD` / 401 미로그인 / 429 `RATE_LIMIT_EXCEEDED`

---

#### POST `/api/v1/auth/signup` · GET `/api/v1/auth/username/check` · POST `/api/v1/invite-codes/validate` · GET `/api/v1/organizations/departments`

→ **[signup.md](./signup.md)** 전문

---

### 2.2 사용자 · 마이페이지

#### GET `/api/v1/users/me`

**Auth:** User · **Response:** `UserProfileResponse` (`userId`, `loginId`, `email`, `name`, `department`, `role`, `status`, `darkMode`, `themeMode`, `createdAt`, `organizationType`, `organizationId`, `organizationName`)

#### PATCH `/api/v1/users/me`

**Auth:** User · **Body:** `UpdateUserProfileRequest`  
**Errors:** `INVALID_PASSWORD`, `DUPLICATE_LOGIN_ID`, `PASSWORD_TOO_SHORT`

#### GET `/api/v1/mypage/analysis-history`

**Auth:** User  
**Query:** `sort=newest|oldest|status`, `page`, `size`  
**Response:** `AnalysisHistoryPageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`)

**`content[]` item: `CaseSummaryResponse`** (FE `CaseSummary`와 동일 — **사건 단위 집계**)

| 필드 | 타입 | 설명 |
| :--- | :--- | :--- |
| `caseId` | string | 사건 식별자 (`caseNumber` → `caseName` → `EVIDENCE-{id}`) |
| `caseName` | string | 사건명 |
| `status` | string | `PENDING` · `PROCESSING` · `COMPLETED` · `FAILED` (사건 내 증거 상태 집계) |
| `createdAt` | string | ISO-8601 UTC — 사건 내 **가장 이른** 분석 요청 시각 |
| `evidenceCount` | number | 해당 사건에 속한 증거 수 |
| `representativeFileName` | string? | 최근 분석 요청 증거의 파일명 |
| `riskScore` | number? | 사건 내 완료 분석 결과의 **최대** riskScore |
| `organizationId` | string? | 기관 식별자 (§0.11) |
| `department` | string? | 담당 부서 |
| `createdBy` | string? | 사건 생성자 userId |
| `assigneeId` | string? | 담당자 userId |
| `reviewerId` | string? | 배정 검토자 userId |
| `reviewStatus` | string? | 검토 상태 (§0.11) |
| `aiResult` | string? | `낮음` · `검토 필요` · `위험` |
| `reviewRequestedAt` | string? | 검토 요청 시각 (ISO-8601 UTC) |

```json
{
  "content": [
    {
      "caseId": "2026-서울-0123",
      "caseName": "2026-서울-0123",
      "status": "COMPLETED",
      "createdAt": "2026-06-18T05:00:00Z",
      "evidenceCount": 2,
      "representativeFileName": "evidence-a.mp4",
      "riskScore": 72.0
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

**Alias:** `GET /api/v1/cases/me` (동일 handler)

---

### 2.3 증거 · 분석

> **경로:** 아래 예시는 §2 canonical 표기. **운영·FE 연동은 `/api/v1/evidences/*`만** (§0, legacy alias 제거됨).

#### GET `/api/v1/evidences/dashboard/intro`

| | |
|---|---|
| **RQ** | RQ-DSH-041 |
| **Auth** | User |

**Response 200:** `DashboardIntroResponse` — 메인 대시보드 히어로 배너·CTA·핵심 가치 카드 (FE `HeroPanel`과 동일 문구)

```json
{
  "badgeLabel": "디지털 포렌식 증거 검증 플랫폼",
  "titleLine1": "디지털 미디어 파일",
  "titleLine2": "분석 대시보드",
  "description": "업로드된 영상 파일의 딥페이크 여부를 AI로 분석하고, ...",
  "shortcuts": [
    {
      "label": "분석 시작하기",
      "actionType": "IN_APP",
      "actionTarget": "#new-analysis",
      "variant": "primary"
    },
    {
      "label": "비교 검증",
      "actionType": "ROUTE",
      "actionTarget": "/compare",
      "variant": "outline"
    }
  ],
  "trustHighlights": [
    { "label": "CoC 감사 추적", "iconKey": "history" },
    { "label": "SHA-256 해시 검증", "iconKey": "check-circle" },
    { "label": "영상 딥페이크 분석", "iconKey": "layers" }
  ]
}
```

---

#### GET `/api/v1/evidences/stats`

| | |
|---|---|
| **RQ** | RQ-DSH-043 |
| **Auth** | User |

**Response 200**

```json
{
  "totalAnalysisCount": 0,
  "deepfakeDetectedCount": 0,
  "completedCount": 0,
  "inProgressCount": 0
}
```

> **RQ-PER-155:** 동일 사용자 요청은 **30초 in-memory 캐시** (`DashboardStatsCache`). 분석 완료·실패·시작 시 `invalidate`.

> 업로드 미디어: **영상(VIDEO)만** 지원.

---

#### GET `/api/v1/evidences/stats/trend`

| | |
|---|---|
| **RQ** | RQ-DSH-044 |
| **Auth** | User |

| Query | Required | Default | 설명 |
| :--- | :---: | :--- | :--- |
| `days` | ❌ | `7` | 조회 일수 (1~30). 오늘 포함 과거 N일 |

**Response 200**

```json
{
  "days": 7,
  "points": [
    { "date": "2026-06-12", "completedCount": 2 },
    { "date": "2026-06-13", "completedCount": 0 }
  ]
}
```

- `points` 길이 = `days` (시작일~오늘, 일별)
- `completedCount`: 해당 일 `AnalysisRequest.status = COMPLETED` 이고 `completedAt`이 그 날짜인 건수 (본인 업로드·미삭제 증거만)

**Errors:** `INVALID_REQUEST` (days 범위 초과)

---

#### GET `/api/v1/evidences/stats/recent`

| | |
|---|---|
| **RQ** | RQ-DSH-045 |
| **Auth** | User |

| Query | Required | Default | 설명 |
| :--- | :---: | :--- | :--- |
| `limit` | ❌ | `5` | 조회 건수 (3~5) |

**Response 200:** `RecentAnalysisResponse`

```json
{
  "limit": 5,
  "items": [
    {
      "evidenceId": 12,
      "analysisRequestId": 101,
      "fileName": "sample.mp4",
      "requestedAt": "2026-06-18T04:30:00Z",
      "status": "COMPLETED",
      "riskScore": 72.5,
      "riskLevel": "HIGH",
      "verdictIndicator": "DANGER"
    },
    {
      "evidenceId": 11,
      "analysisRequestId": 98,
      "fileName": "pending.mp4",
      "requestedAt": "2026-06-18T03:00:00Z",
      "status": "PROCESSING"
    }
  ]
}
```

| 필드 | 설명 |
| :--- | :--- |
| `items[].fileName` | 영상 파일명 |
| `items[].requestedAt` | 분석 요청 일시 (UTC) |
| `items[].status` | `PENDING` · `PROCESSING` · `COMPLETED` · `FAILED` |
| `items[].riskScore` | **COMPLETED** 시 위험 지수(%) |
| `items[].riskLevel` | **COMPLETED** 시 `LOW` · `MEDIUM` · `HIGH` |
| `items[].verdictIndicator` | **COMPLETED** 시 `NORMAL`(초록) · `SUSPICIOUS`(주황) · `DANGER`(빨강) |

- 본인(`requestedBy`) · 미삭제 증거만 · **증거당 최신 분석 요청 1건** · `requestedAt` 내림차순
- 진행 중·실패 건은 `riskScore` / `riskLevel` / `verdictIndicator` **미포함**

**Errors:** `INVALID_REQUEST` (limit 범위 초과)

---

#### POST `/api/evidences/upload`

| | |
|---|---|
| **RQ** | RQ-REQ-047, RQ-REQ-048 |
| **Auth** | User |
| **Content-Type** | `multipart/form-data` |

| Field | Required |
| :--- | :---: |
| `file` | ✅ |
| `caseName` | ✅ |

**Response 200:** `FileUploadResponse` (`evidenceId`, `hashValue`, `metadata`, …)

**허용 파일:** 영상 **MP4, MOV** only (`FileValidationService`)

**Errors:** `FILE_NOT_FOUND`, `UNSUPPORTED_FILE_TYPE`, `FILE_SIZE_EXCEEDED`, `HASH_GENERATION_FAILED`  
**Servlet multipart 한도 초과:** HTTP **413** + `FILE_TOO_LARGE` (`MaxUploadSizeExceededException` — `spring.servlet.multipart.max-file-size` 기준)

**명세 목표 path:** `POST /api/v1/evidences/upload`

---

#### POST `/api/evidences/analyze`

| | |
|---|---|
| **RQ** | RQ-REQ-049 |
| **Auth** | User |

**Request (현재)**

```json
{
  "evidenceIds": [1, 2],
  "caseName": "2026-서울-0123 사건"
}
```

**Response 200:** `StartAnalysisResponse` (`success`, `startedCount`, `evidenceIds`, …)

**RQ-REQ-050 (분석 시작 시 선행):** 각 evidence에 대해 S3 증명 사본 생성 → SHA-256 검증 → Evidence Manifest 생성·X.509 서명 → CoC 기록 후 분석 큐 등록. 사본 생성 실패 시 **422** + `ANALYSIS_COPY_CREATE_FAILED` / `ANALYSIS_COPY_VERIFY_FAILED`.

**명세 목표 Request**

```json
{ "evidenceId": 1 }
```

**명세 목표 path:** `POST /api/v1/evidences/analyze`

---

#### GET `/api/evidences/{evidenceId}/analysis-status`

| | |
|---|---|
| **RQ** | RQ-DTL-055, RQ-REQ-049 (polling) |
| **Auth** | User |

**Response 200:** `AnalysisStatusResponse`

| 필드 | 설명 |
| :--- | :--- |
| `evidenceId` | 증거 ID |
| `analysisRequestId` | 최신 분석 요청 ID (없으면 `0`) |
| `status` | `PENDING` · `PROCESSING` · `COMPLETED` · `FAILED` |
| `queueStatus` | `WAITING` · `ANALYZING` · `COMPLETED` · `FAILED` (SK-923) |
| `progressPercent` | 0~100 |
| `queuePosition` | **`status=QUEUED`(대기)일 때만** — 대기열 순번 (1부터) · WBS 3.1.4 |
| `queueDepth` | **`status=QUEUED`일 때만** — 현재 전체 대기 건수 |
| `errorCode` | **`FAILED`일 때만** — 예: `ANALYSIS_FAILED`, `RABBITMQ_PUBLISH_FAILED`, **`ANALYSIS_TIMEOUT`** |
| `errorMessage` | **`FAILED`일 때만** — 실패 사유 요약 |

```json
{
  "evidenceId": 12,
  "analysisRequestId": 101,
  "status": "FAILED",
  "progressPercent": 0,
  "errorCode": "RABBITMQ_PUBLISH_FAILED",
  "errorMessage": "분석 요청 큐 등록에 실패했습니다."
}
```

**Errors:** `EVIDENCE_NOT_FOUND` (404) — 타 사용자 증거 또는 삭제됨

---

#### GET `/api/v1/evidences/{evidenceId}/detail`

| | |
|---|---|
| **RQ** | RQ-DTL-053~076 |
| **Auth** | User (Bearer JWT 필수) |
| **Headers** | `X-Step-Up-Token` (필수) — `POST /api/v1/auth/step-up/verify`로 발급 |

**Request**

```http
GET /api/v1/evidences/12/detail
Authorization: Bearer <accessJwt>
X-Step-Up-Token: <stepUpToken>
```

**Response:** `EvidenceDetailResponse`

| 필드 | 설명 |
| :--- | :--- |
| `evidenceInfo` | `caseId` 포함 (FE `EvidenceInfo.caseId`) — presigned `videoUrl`/`previewUrl` 제거 |
| `hlsPlayback` | HLS 재생 메타 + `streamToken` (§0.14) |
| `manifestInfo` | **RQ-DTL-075** — 분석 사본 생성 후 Manifest 요약 (없으면 `null`) |
| `signatureInfo` | **RQ-DTL-076** — X.509 전자서명 상태 (`signatureStatus` · `signatureValid`) |

```json
{
  "evidenceInfo": { },
  "integrityInfo": {
    "recoveryScore": 85,
    "dataLossPercent": 15,
    "recoveryGrade": "HIGH",
    "chainValid": true
  },
  "manifestInfo": {
    "evidenceId": 12,
    "fileId": 12,
    "caseId": "2026-서울-0123",
    "caseNumber": "2026-서울-0123",
    "originalHash": "...",
    "uploadedAt": "2026-06-18T05:00:00Z",
    "copyHash": "...",
    "manifestCreatedAt": "2026-06-18T06:00:00Z",
    "manifestHash": "...",
    "issuer": "ForenShield Digital Forensics"
  },
  "signatureInfo": {
    "signatureStatus": "SIGNED",
    "signatureAlgorithm": "SHA256withRSA",
    "signedAt": "2026-06-18T06:00:00Z",
    "signerCertificateSubject": "CN=ForenShield Forensics CA,O=ForenShield Platform,C=KR",
    "signatureValid": true
  },
  "analysisInfo": {
    "riskScore": 85.5,
    "confidenceScore": 0.94,
    "riskLevel": "HIGH",
    "modelScores": [
      { "moduleName": "deepfake", "detected": true, "score": 0.92, "modelName": "ForenShield-DF", "modelVersion": "1.2.0" }
    ],
    "evidenceItems": ["Deepfake face detection score is exceptionally high (0.92)"],
    "frameRisks": [{ "frameIndex": 0, "timestampSec": 12.0, "riskScore": 0.82 }],
    "suspiciousSegments": [{ "startTime": 12.0, "endTime": 15.0, "maxRiskScore": 0.82, "reason": "high risk frames" }],
    "moduleResults": [
      {
        "moduleName": "deepfake",
        "detected": true,
        "score": 0.92,
        "confidence": 0.88,
        "modelName": "ForenShield-DF",
        "modelVersion": "1.2.0",
        "details": "{}"
      }
    ]
  },
  "cocLogs": [ ]
}
```

- Manifest·서명은 **분석 시작 시 사본 생성**(`RQ-REQ-050`)과 함께 생성됨
- 업로드만 한 상태: `manifestInfo=null`, `signatureInfo.signatureStatus=UNSIGNED`
- **RQ-SEC-153:** 조회 시 Manifest 서명·CoC 체인·블록체인 해시를 검증하고, 실패 시 `SECURITY_ALERT` 알림 자동 생성 (응답은 200 유지)

**Errors:** 403 `STEP_UP_REQUIRED` — `X-Step-Up-Token` 누락·만료·불일치 / 404 `EVIDENCE_NOT_FOUND`

---

#### GET `/api/v1/evidences/{evidenceId}/integrity/verify`

| | |
|---|---|
| **RQ** | RQ-SEC-153, SK-632 |
| **Auth** | User |

증거 무결성·서명·블록체인 해시를 검증합니다. 실패 시 보안 알림도 발송합니다.

**Response 200:** `IntegrityVerifyResponse`

| 필드 | 설명 |
| :--- | :--- |
| `evidenceId` | 증거 ID |
| `valid` | 전체 검증 통과 여부 |
| `checks` | 항목별 결과 (`SIGNATURE`, `COC_CHAIN`, `BLOCKCHAIN_HASH`) |

```json
{
  "evidenceId": 12,
  "valid": true,
  "checks": [
    { "checkType": "SIGNATURE", "valid": true, "message": "Manifest 서명이 유효합니다." },
    { "checkType": "COC_CHAIN", "valid": true, "message": "CoC 해시 체인이 유효합니다." },
    { "checkType": "BLOCKCHAIN_HASH", "valid": true, "message": "앵커링된 블록체인 기록이 없습니다." }
  ]
}
```

**Response 409:** `StandardErrorResponse` — 첫 번째 실패 항목의 `errorCode`

```json
{
  "success": false,
  "errorCode": "SIGNATURE_INVALID",
  "message": "Evidence Manifest X.509 서명 검증에 실패했습니다."
}
```

| errorCode | 의미 |
| :--- | :--- |
| `SIGNATURE_INVALID` | Manifest X.509 서명 검증 실패 |
| `CHAIN_INTEGRITY_FAILED` | CoC 해시 체인 불일치 |
| `BLOCKCHAIN_HASH_MISMATCH` | 블록체인 앵커 해시 ≠ 현재 원본 해시 |

**Errors:** `EVIDENCE_NOT_FOUND` (404)

#### GET `/api/evidences/{evidenceId}/coc/verify`

| | |
|---|---|
| **RQ** | RQ-HIS-107 |
| **Auth** | User |

**Response 200**

```json
{
  "evidenceId": 1,
  "valid": true,
  "logCount": 3,
  "brokenAtLogId": null,
  "failureReason": null,
  "message": "CoC 해시 체인 검증에 성공했습니다."
}
```


---

#### POST `/api/v1/evidences/{evidenceId}/access-events`

| | |
|---|---|
| **RQ** | RQ-REQ-051 |
| **Auth** | User (증거 소유자 — `requireOwned`와 동일) |

**Body**

```json
{
  "eventType": "VIEW",
  "caseKey": "assigned-access-case",
  "source": "protected_video_player"
}
```

| 필드 | 필수 | 설명 |
| :--- | :---: | :--- |
| `eventType` | ✅ | `VIEW` — 영상 열람 · `CAPTURE_ATTEMPT` — 화면 캡처 시도 |
| `caseKey` | | 사건 키 (감사 payload) |
| `source` | | 클라이언트 출처 (예: `protected_video_player`) |

**Response:** **204** No Content

**CoC 기록**

| `eventType` | `actionType` |
| :--- | :--- |
| `VIEW` | `EVIDENCE_VIEWED` |
| `CAPTURE_ATTEMPT` | `EVIDENCE_CAPTURE_ATTEMPTED` |

**Errors:** `EVIDENCE_NOT_FOUND` (404), `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401)

---

#### DELETE `/api/evidences/{evidenceId}`

증거 소프트 삭제 · **204** 또는 error body

#### DELETE `/api/evidences/{evidenceId}/reset`

증거+분석 전체 제거 · **204**

#### DELETE `/api/evidences/{evidenceId}/analysis`

진행 중 분석만 중단 · **204**

---

### 2.4 사건 상세

#### POST `/api/v1/cases`

| | |
|---|---|
| **Auth** | INVESTIGATOR · ORG_ADMIN (JWT) |

**Body**

```json
{ "caseName": "사건명" }
```

**Response 200:** `CaseDetailResponse` — `evidences: []`, `status: "PENDING"`  
**Errors:** `DUPLICATE_CASE_NAME` (409), `INVALID_REQUEST` (400), `FORBIDDEN` (403)

---

#### GET `/api/v1/cases/{caseId}`

| | |
|---|---|
| **RQ** | RQ-DTL-053 + 다수 DTL (명세는 이 API로 상세 전체 제공 가정) |
| **Auth** | User |
| **Path** | `caseId` = **String** (사건명/식별자) |

**Response:** `CaseDetailResponse`  
**`evidences[]` item (`CaseEvidenceSummaryDto`):** `evidenceId`, `fileName`, `mediaType`, `analysisStatus`, `hlsStatus?` — VIDEO 증거 HLS 패키징 상태 (presigned URL 제거, §0.14)  
**Errors:** `CASE_NOT_FOUND` (404)

> 명세 FE: `app/cases/[id]/page.tsx` → 이 API 호출.  
> 코드는 **case + evidence detail API 이중 구조** — FE는 화면 설계에 맞게 선택.

---

### 2.5 관리자 (`/api/v1/admin/**`)

**공통:** `@PreAuthorize("hasRole('ADMIN')")` · **RQ:** RQ-ADMIN-119~141

| Method | Path | 설명 |
| :--- | :--- | :--- |
| GET | `/api/v1/admin/dashboard/stats` | `AdminDashboardStatsResponse` · **RQ-ADMIN-120** |
| GET | `/api/v1/admin/dashboard/analysis-stats` | `AdminAnalysisStatsResponse` · **RQ-ADMIN-150** |
| GET | `/api/v1/admin/users` | 목록 (`search`, `status`, `role`, `page`, `size`) |
| GET | `/api/v1/admin/reviewers` | 검토자 배정용 승인 검토자 목록 (`department?`) |
| POST | `/api/v1/admin/users/{userId}/approve` | 가입 승인 |
| POST | `/api/v1/admin/users/{userId}/reject` | 가입 반려 |
| POST | `/api/v1/admin/users/{userId}/suspend` | 계정 정지 (APPROVED → SUSPENDED) · **RQ-ADMIN-126** |
| PATCH | `/api/v1/admin/users/{userId}` | 정보 수정 |
| PATCH | `/api/v1/admin/users/{userId}/password` | 비밀번호 재설정 |
| DELETE | `/api/v1/admin/users/{userId}` | 계정 삭제 |
| GET | `/api/v1/admin/invite-codes` | 초대코드 목록 |
| POST | `/api/v1/admin/invite-codes` | 초대코드 발급 |
| GET | `/api/v1/admin/evidences` | 증거 목록 (page) |
| GET | `/api/v1/admin/evidences/{evidenceId}` | 증거 상세 |
| DELETE | `/api/v1/admin/evidences/{evidenceId}` | 증거 삭제 |
| GET | `/api/v1/admin/logs` | 감사 로그 (`category`, `from`, `to`, …) |
| GET | `/api/v1/admin/logs/export?format=csv` | CSV 다운로드 |
| GET | `/api/v1/admin/me` | 관리자 프로필 |
| PATCH | `/api/v1/admin/me` | 프로필 수정 |
| PATCH | `/api/v1/admin/me/password` | 비밀번호 변경 |
| POST | `/api/v1/admin/blockchain/merkle/anchor` | Merkle Root **수동** 앵커 (WBS 2.10.2) |

#### POST `/api/v1/admin/blockchain/merkle/anchor`

| | |
|---|---|
| **RQ** | RQ-REQ-052, RQ-SEC-151 |
| **Auth** | `ROLE_ADMIN` |
| **Query** | `batchDate` (optional, ISO date — 미지정 시 전일) |

**Response 200:** `BlockchainAnchorRecordDto` (transactionHash, merkleRoot, anchoredAt, …)

**Errors:** `FORBIDDEN` (403) — 일반 사용자

**Admin 페이지 Response (페이지네이션):** `content`, `page`, `size`, `totalElements`, `totalPages` ([implementation-standards.md §7](../guides/implementation-standards.md))

#### GET `/api/v1/admin/dashboard/analysis-stats`

| | |
|---|---|
| **RQ** | RQ-ADMIN-150 |
| **Auth** | `ROLE_ADMIN` |

관리자 **통계 분석** 화면용 집계 API. 사용자별 `GET /api/v1/evidences/stats`와 달리 **전체 사용자·미삭제 증거**를 대상으로 합니다.  
분석 건수 집계 기준은 [erd.md §8.2](../database/erd.md)와 동일하게 **COMPLETED** 완료 건수를 사용합니다.

**Response 200:** `AdminAnalysisStatsResponse`

```json
{
  "weeklyTotalCount": 12,
  "deepfakeDetectionRate": 15.6,
  "averageAnalysisMinutes": 3.2,
  "weeklyPoints": [
    {
      "label": "월",
      "date": "2026-06-16",
      "requestedCount": 4,
      "completedCount": 3
    }
  ],
  "riskDistribution": {
    "safeCount": 8,
    "cautionCount": 2,
    "dangerCount": 2
  }
}
```

| 필드 | 설명 |
| :--- | :--- |
| `weeklyTotalCount` | **이번 주(월~일)** `COMPLETED` 건수 (`completedAt` 기준, 미삭제 증거) |
| `deepfakeDetectionRate` | 이번 주 완료 분석 중 `riskLevel` MEDIUM·HIGH 비율 (%, 소수 1자리) |
| `averageAnalysisMinutes` | 이번 주 완료 분석 평균 소요 시간(분). `startedAt` 없으면 `requestedAt`~`completedAt` |
| `weeklyPoints` | 이번 주 7일(월~일) 일별 추이 |
| `weeklyPoints[].requestedCount` | 해당 일 `requestedAt` 기준 분석 요청 건수 |
| `weeklyPoints[].completedCount` | 해당 일 `completedAt` 기준 완료 건수 |
| `riskDistribution` | 전체 완료 분석의 `riskScore` 구간별 건수 (0~49 적합, 50~79 주의, 80~100 위험) |

승인·반려·정지 응답 `AdminUserStatusResponse`에 **`processedByUserId`** (처리 관리자 ID) 포함.

---

### 2.6 비교 검증 (`/api/v1/compare/**`)

**Auth:** User · **RQ:** RQ-CMP-091~104

| Method | Path | 설명 |
| :--- | :--- | :--- |
| GET | `/api/v1/compare/originals` | 원본 증거 목록·검색 (`search`, `page`, `size`) |
| GET | `/api/v1/compare/originals/{evidenceId}` | 원본 파일 기본정보 |
| POST | `/api/v1/compare/verify` | 비교 검증 실행 (`evidenceId`, `file`, 선택 `requestId`) |
| POST | `/api/v1/compare/cancel` | 클라이언트 취소 토큰 수신 (**204**, 동기 처리 no-op) |
| GET | `/api/v1/compare/{compareId}` | 비교 결과 조회 |
| GET | `/api/v1/compare/{compareId}/candidate` | 대조본 파일 기본정보 |
| GET | `/api/v1/compare/{compareId}/reports/pdf` | 비교 PDF (원본/대조본 기본정보 섹션) |

**Response `CompareFileInfoDto`:** `evidenceId`, `fileName`, `fileSize`, `sha256`, `caseName`, `mimeType`, `uploadedAt` 등

**Response `CompareResult` / `CompareVerifyResponse` (FE `CompareResult`):** `compareId`, `originalEvidenceId`, `candidateFileName`, `verdict`, `summary`, `items`, `createdAt` 외

| 필드 | 타입 | 설명 |
| :--- | :--- | :--- |
| `signature` | object | 전자서명 신뢰 레이어 |
| `signature.originalStatus` | `VALID` \| `INVALID` \| `UNSIGNED` | 기준 증거 매니페스트 서명 상태 |
| `signature.candidateStatus` | `VALID` \| `INVALID` \| `UNSIGNED` | 비교 대상(업로드 파일) — 항상 `UNSIGNED` |
| `signature.algorithm` | string? | 기준 증거 서명 알고리즘 |
| `signature.signedBy` | string? | 서명자 인증서 Subject |
| `signature.signedAt` | string? | 서명 일시 (UTC ISO-8601) |
| `blockchain` | object | 블록체인 등록 해시 대조 레이어 |
| `blockchain.status` | `MATCH` \| `MISMATCH` \| `NOT_ANCHORED` | 앵커 `subject_hash` vs 기준 증거 `originalHashValue` |
| `blockchain.network` | string? | 네트워크 |
| `blockchain.txHash` | string? | 트랜잭션 해시 |
| `blockchain.blockNumber` | number? | 블록 번호 |
| `blockchain.anchoredAt` | string? | 앵커링 일시 |
| `blockchain.anchoredHash` | string? | 등록된 `subject_hash` |

---

## 3. 기능명세서 목표 API (향후 정렬)

기능명세서 FE「호출 API」+ RQ에서 **명시·유도**되는 REST 계약입니다. §2와 다른 항목은 §0 Gap 표 참조.

| Method | Path (목표) | RQ | 구현 |
| :---: | :--- | :--- | :---: |
| POST | `/api/auth/login` | LOGIN-020 | ✅ |
| POST | `/api/v1/auth/signup` | SIGNUP-037 | ✅ |
| POST | `/api/v1/invite-codes/validate` | SIGNUP-034 | ✅ |
| GET | `/api/v1/evidences/stats` | DSH-043 | ✅ |
| GET | `/api/v1/evidences/stats/trend` | DSH-044 | ✅ |
| GET | `/api/v1/admin/dashboard/analysis-stats` | ADMIN-150 | ✅ |
| GET | `/api/v1/mypage/analysis-history` | DSH-045, HIS | ✅ |
| POST | `/api/v1/evidences/upload` | REQ-047~048 | 🟡 path |
| POST | `/api/v1/evidences/analyze` | REQ-049 | 🟡 path+body |
| GET | `/api/v1/cases/{caseId}` | DTL-* | ✅ |
| GET/PATCH | `/api/v1/users/me` | MYP | ✅ |
| * | `/api/v1/admin/*` | ADMIN-* | ✅ |
| GET | `/api/v1/evidences/{evidenceId}/reports/pdf` | DTL-084~087 | ✅ |
| GET | `/api/v1/compare/originals` | CMP-091 | ✅ |
| POST | `/api/v1/compare/verify` | CMP-092~104 | ✅ |
| GET | `/api/v1/notifications` | COM-015 | ✅ |
| GET/PATCH | `/api/v1/users/me/settings` | COM-009 | ✅ |

---

## 4. AI · 비동기 (REST 아님)

[RabbitMQ / JSON: ../integrations/ai-json.md](../integrations/ai-json.md) · [../integrations/rabbitmq.md](../integrations/rabbitmq.md)

---

## 5. Swagger

로컬: `http://localhost:8080/swagger-ui.html`

---

## 6. Quick Reference — 현재 구현 전체 (Controller 기준, 2026-07-14)

> 출처: `src/main/java/.../controller/*` · FE: `frontend-deepfake/lib/**`  
> Auth: **Public** / **User**(JWT) / **Admin**(ORG_ADMIN·ADMIN) / **Step-up**(추가 헤더)  
> FE: ✅ 연동 · ❌ BE 전용·미연동

### 6.1 인증 · 가입 · Step-up

| Method | Path | Auth | FE |
| :--- | :--- | :--- | :---: |
| POST | `/api/auth/login` | Public | ✅ |
| POST | `/api/auth/refresh` | Cookie | ✅ |
| POST | `/api/auth/logout` | User | ✅ |
| POST | `/api/v1/auth/signup` | Public | ✅ |
| GET | `/api/v1/auth/username/check` | Public | ✅ |
| POST | `/api/v1/auth/step-up/verify` | User | ✅ |
| POST | `/api/v1/auth/step-up/extend` | User | ✅ |
| POST | `/api/v1/invite-codes/validate` | Public | ✅ |
| GET | `/api/v1/organizations/departments` | Public | ✅ |

### 6.2 사용자 · 마이페이지 · 알림

| Method | Path | Auth | FE |
| :--- | :--- | :--- | :---: |
| GET | `/api/v1/users/me` | User | ✅ |
| PATCH | `/api/v1/users/me` | User | ✅ |
| GET | `/api/v1/users/me/settings` | User | ✅ |
| PATCH | `/api/v1/users/me/settings` | User | ✅ |
| GET | `/api/v1/mypage/analysis-history` | User | ✅ |
| GET | `/api/v1/cases/me` | User | ✅ alias |
| GET | `/api/v1/notifications` | User | ✅ |
| PATCH | `/api/v1/notifications/{id}/read` | User | ✅ |
| PATCH | `/api/v1/notifications/read-all` | User | ✅ |

### 6.3 사건 · 검토 워크플로우

| Method | Path | Auth | FE |
| :--- | :--- | :--- | :---: |
| GET | `/api/v1/cases` · `/api/v1/cases/{caseId}` (`?caseKey=`) | User | ✅ |
| POST | `/api/v1/cases` | User | ✅ |
| PATCH | `/api/v1/cases?caseKey=` | User | ✅ 사건명 |
| PATCH | `/api/v1/cases/representative?caseKey=` | User | ✅ |
| POST | `/api/v1/cases/review-request?caseKey=` | User | ✅ |
| PATCH | `/api/v1/cases/reviewer?caseKey=` | Admin | ✅ |
| PATCH | `/api/v1/cases/{caseId}/reviewer` | Admin | ✅ |
| POST | `/api/v1/cases/review-decision?caseKey=` | Reviewer/Admin | ✅ |

### 6.4 증거 · 분석 · 대시보드 · HLS · 리포트

| Method | Path | Auth | FE |
| :--- | :--- | :--- | :---: |
| POST | `/api/v1/evidences/upload` | User | ✅ |
| POST | `/api/v1/evidences/analyze` | User | ✅ |
| GET | `/api/v1/evidences/{id}/detail` | User (+ Step-up) | ✅ |
| GET | `/api/v1/evidences/{id}/analysis-status` | User | ✅ |
| GET | `/api/v1/evidences/{id}/readiness` | User | ✅ |
| POST | `/api/v1/evidences/{id}/readiness-check` | User | ✅ |
| DELETE | `/api/v1/evidences/{id}` | User | ✅ 업로드 취소 |
| DELETE | `/api/v1/evidences/{id}/reset` | User | ✅ |
| DELETE | `/api/v1/evidences/{id}/analysis` | User | ✅ 분석 중단 |
| PATCH | `/api/v1/evidences/{id}/exclude` | User | ✅ |
| POST | `/api/v1/evidences/{id}/replace` | User | ✅ |
| PATCH | `/api/v1/evidences/{id}/role` | User | ✅ |
| POST | `/api/v1/evidences/{id}/access-events` | User | ✅ |
| GET | `/api/v1/evidences/stats` | User | ✅ |
| GET | `/api/v1/evidences/stats/trend` | User | ✅ |
| GET | `/api/v1/evidences/stats/recent` | User | ✅ |
| GET | `/api/v1/evidences/dashboard/intro` | User | ❌ |
| GET | `/api/v1/evidences/{id}/reports/pdf` | User (+ Step-up) | ✅ |
| GET | `/api/v1/evidences/{id}/reports/verify` | User | ❌ |
| GET | `/api/v1/evidences/{id}/integrity/verify` | User | ✅ |
| GET | `/api/v1/evidences/{id}/coc/verify` | User | ❌ |
| GET | `/api/v1/evidences/{id}/blockchain` | User | ✅ |
| GET | `/api/v1/evidences/{id}/hls/master.m3u8` | User + streamToken | ✅ |
| GET | `/api/v1/evidences/{id}/hls/key` | User + streamToken + Step-up | ✅ |
| GET | `/api/v1/evidences/{id}/hls/segments/{file}` | User + streamToken | ✅ |

### 6.5 보고서 · 공개 검증

| Method | Path | Auth | FE |
| :--- | :--- | :--- | :---: |
| GET | `/api/v1/reports` | User | ✅ |
| POST | `/api/v1/reports/{reportId}/public-access` | User | ✅ |
| GET | `/api/v1/public/reports/verify` | Public | ✅ |
| POST | `/api/v1/public/reports/verify-file-hash` | Public | ✅ |
| GET | `/api/v1/public/reports/view` | Public | ✅ |
| GET | `/api/v1/public/reports/view/pdf` | Public | ✅ |

발행된 보고서의 `GET /api/v1/public/reports/verify` 응답은 발행 레지스트리 정보에 다음 스냅샷 요약 필드를 포함합니다.

| 필드 | 설명 |
| :--- | :--- |
| `analysisVerdict` | 발행 시점 범주형 결과 (`의심 신호 확인`, `의심 신호 미확인`, `판정 불가` 등) |
| `analysisCompletedAt` | 발행 스냅샷에 기록된 분석 완료 시각 |
| `snapshotSchemaVersion` | 공개 요약 데이터 계약 버전 |
| `pdfTemplateVersion` | 발행에 사용된 PDF 템플릿 버전 |
| `evidenceManifestSignatureValid` | 발행 시점 증거 매니페스트 서명 유효 여부. 기록 없음·미발행은 `null` |
| `evidenceManifestSignatureStatus` | 발행 시점 증거 매니페스트 서명 상태 (`VALID`, `INVALID`, `NOT_FOUND`, `FAILED`) |
| `evidenceManifestSignatureAlgorithm` | 발행 시점 서명 알고리즘. 기록이 없으면 `null` |
| `evidenceManifestSignerCertificateSubject` | 발행 시점 서명 인증서 Subject. 기록이 없으면 `null` |
| `blockchainStatus` | 최종 PDF SHA-256의 보고서 블록체인 등록 상태 |
| `blockchainMatched` | 등록된 보고서 해시와 현재 발행 해시의 일치 여부 |
| `blockchainNetwork` | 보고서 해시가 등록된 네트워크. `local-simulated`는 개발 검증용 |
| `blockchainTxHash` | 보고서 해시 등록 트랜잭션 ID |
| `blockchainAnchoredAt` | 보고서 해시 등록 완료 시각 |

- 숫자 점수·기준값, 사건명, 원본 파일명, 담당자, 의심 구간 및 프레임은 공개 응답에 포함하지 않습니다.
- 발행 후 현재 분석 결과가 변경되어도 공개 요약은 기존 `ReportPublicationSnapshots` 값을 유지합니다.
- `signatureValid`, `signatureStatus`, `signatureAlgorithm`, `signerCertificateSubject`, `pdfSignatureApplied`는 이전 클라이언트 호환용 deprecated 필드입니다. 신규 화면은 사용하지 않습니다.
- `signature*`는 과거 명칭과 달리 PDF 전자서명이 아니라 증거 매니페스트 서명을 뜻합니다. 신규 연동은 반드시 `evidenceManifestSignature*`를 사용합니다.
- 아직 발행되지 않았거나 스냅샷이 없는 레거시 보고서는 위 스냅샷 요약 필드가 `null`일 수 있습니다.

### 6.6 비교 검증

| Method | Path | Auth | FE |
| :--- | :--- | :--- | :---: |
| GET | `/api/v1/compare/originals` | User | ✅ |
| GET | `/api/v1/compare/originals/{evidenceId}` | User | ✅ |
| POST | `/api/v1/compare/verify` | User | ✅ multipart |
| POST | `/api/v1/compare/verify-registered` | User | ✅ |
| POST | `/api/v1/compare/cancel` | User | ✅ |
| GET | `/api/v1/compare/{compareId}` | User | ✅ |
| GET | `/api/v1/compare/{compareId}/candidate` | User | △ 결과 UI 간접 |
| GET | `/api/v1/compare/{compareId}/reports/pdf` | User | ✅ |

### 6.7 관리자

| Method | Path | Auth | FE |
| :--- | :--- | :--- | :---: |
| GET | `/api/v1/admin/dashboard/stats` | Admin | ✅ |
| GET | `/api/v1/admin/dashboard/analysis-stats` | Admin | ✅ |
| GET | `/api/v1/admin/users` | Admin | ✅ |
| POST | `/api/v1/admin/users/{id}/approve` | Admin | ✅ |
| POST | `/api/v1/admin/users/{id}/reject` | Admin | ✅ |
| POST | `/api/v1/admin/users/{id}/suspend` | Admin | ✅ |
| POST | `/api/v1/admin/users/{id}/reactivate` | Admin | ✅ |
| PATCH | `/api/v1/admin/users/{id}` | Admin | ✅ |
| PATCH | `/api/v1/admin/users/{id}/password` | Admin | ✅ |
| DELETE | `/api/v1/admin/users/{id}` | Admin | ✅ |
| GET | `/api/v1/admin/reviewers` | Admin | ✅ |
| GET/POST | `/api/v1/admin/invite-codes` | Admin | ✅ |
| GET | `/api/v1/admin/evidences` · `/{id}` | Admin | ✅ |
| DELETE | `/api/v1/admin/evidences/{id}` | Admin | ✅ |
| GET | `/api/v1/admin/logs` · `/export` | Admin | ✅ |
| GET/PATCH | `/api/v1/admin/me` · `/password` | Admin | ✅ |
| GET | `/api/v1/admin/coc/chains` | Admin | ✅ |
| POST | `/api/v1/admin/blockchain/merkle/anchor` | Admin | △ 운영/도구 |

---

## 7. 변경 이력

| 날짜 | 버전 | 내용 |
| :--- | :--- | :--- |
| 2026-07-14 | v1.9 | **문서 동기화:** §6 Quick Reference를 Controller 전면 재작성(`/api/v1` only) · §0.7 FE 연동 표(알림·설정·무결성·블록체인·검토·HLS) 갱신 · v1.8 헤더 §번호 정리 |
| 2026-07 | v1.8 | **RBAC·검토(§0.12):** review-request/reviewer/review-decision · admin reviewers · case/mypage 필드 · HLS(§0.14) · step-up · access-events(§0.11) · 보고서 목록·공개 검증 · v2 case workflow |
| 2026-06-19 | v1.7 | **FE develop 연동:** analysis-history → **사건 단위 `CaseSummaryResponse`** · `POST /compare/cancel` · `evidenceInfo.caseId` · `CaseEvidenceSummaryDto` 미디어 URL 필드(nullable) |
| 2026-06-19 | v1.6 | **Sprint 4·5:** `queuePosition`/`queueDepth` · `ANALYSIS_TIMEOUT` · `FILE_TOO_LARGE` 413 · admin Merkle 앵커 · PDF CoC `REPORT_*` · stats 캐시 |
| 2026-06-19 | v1.5 | analysis-history **증거 단위 필드** · analyze `results[].queueRegistered` · status **`queueStatus`**(WAITING/ANALYZING) · detail CoC 검증 필드 |
| 2026-06-19 | v1.4 | compare **originals/candidate** API · admin **suspend** · detail `moduleResults` modelName/modelVersion/confidence · §2.6 추가 |
| 2026-06-18 | v1.3 | `GET /api/v1/admin/dashboard/analysis-stats` 추가 (RQ-ADMIN-150) |
| 2026-06-17 | v1.2 | 기능명세서 대비 Gap 분석 + 현재 코드 정본 분리 |
| 2026-06-17 | v1.1 | Excel 명세 반영 초안 |
| 2026-06-09 | v1.0 | upload 중심 초안 |
