# ForenShield 프로젝트 진행 상황

> **작성일:** 2026-06-17  
> **최종 갱신:** 2026-06-19 (Sprint 4·5 BE 보완 · 리팩토링)  
> **기준:** `docs/requirements/source/` Excel · **영상(VIDEO)만** 지원

---

## 갱신 규칙 (팀 합의)

**주 1회** (스프린트 회의 전·후, 담당: BE 리드 또는 PM) 본 문서 상태를 맞춥니다.

| 주기 | 갱신 대상 | 하지 않음 |
| :--- | :--- | :--- |
| **매주 1회** | §1 한눈에 보기 · §2 영역별 표 · §8 P0/P1 · §9 파트별 요약 · **최종 갱신** 날짜 | §3 API 전체 목록 재작성 |
| **PR 머지 시** (선택) | §12 변경 이력 1줄 · §2 해당 RQ 행만 | §1 퍼센트 재계산 |
| **마일스톤·스프린트 종료** | §3 API 목록 · §6 RTM 교차검토 · §10 다음 스프린트 | — |

**매주 체크리스트 (5~10분)**

- [ ] 코드·`api/specification.md` Gap과 §2 상태 emoji 일치
- [ ] 완료된 P0/P1을 §8에서 ✅ 처리 또는 제거
- [ ] §12에 이번 주 주요 완료 1~3줄 추가
- [ ] 상단 **최종 갱신** 날짜 수정

**AI로 갱신할 때** (토큰 절약): `@docs/PROJECT_STATUS.md` 첨부 후  
「§1·§2·§8·§12와 최종 갱신 날짜만 업데이트. §3·§9 전체 재작성 금지」라고 지시.

개별 PR마다 전 문서를 다시 쓰지 않습니다. 상세 작업 기록은 PR 본문·Jira에 두고, 본 문서는 **팀 공용 현황판**만 유지합니다.

---

## 1. 한눈에 보기

| 구분 | 규모 | 진행 (본 레포 BE 기준) | 비고 |
| :--- | :---: | :---: | :--- |
| **요구사항 (RQ)** | 169건 | **약 55~60%** | source Excel 재추출 (2026-06-18) |
| **기능 (FN)** | BE 115건 | **BE FN 약 65%** | [traceability.md](./requirements/traceability.md) 재생성 |
| **REST API (BE)** | ~48 엔드포인트 | **MVP ✅** | PDF·Compare·알림·무결성검증·S4-5 큐/앵커 포함 |
| **팀 문서 (`docs/`)** | 23개 md + Excel source | **✅ 정비 완료** | [AGENTS.md](./AGENTS.md) 진입점 |
| **테스트** | 30 test 클래스 | **✅ 전체 통과** | `./gradlew test` (136 tests) |
| **배포 브랜치** | `main` | **✅ PR #25 merge (2026-06-19)** | `develop` → `main` (`f2f12fe`) |

```mermaid
pie title 백엔드 RQ 영역별 구현 상태 (추정)
    "완료" : 45
    "부분" : 15
    "미구현" : 25
    "타 파트" : 15
```

> **해석:** 본 레포는 **Spring Boot API** 중심. MVP REST·CoC·Manifest/**플랫폼 X.509(PKCS#8)**·PDF·Compare·알림까지 **코드 반영 완료**. 남은 건 **INF Secrets Manager 운영 키 등록·블록체인 게이트웨이·AI 실연동** 쪽입니다.

---

## 2. 영역별 진행 현황

| 영역 | 대표 RQ | 백엔드 API | 상태 | 설명 |
| :--- | :--- | :--- | :---: | :--- |
| **공통·인증** | RQ-COM-*, NFR-160~162 | JWT · `@PreAuthorize` | ✅ | `AuthUserResolver`, Admin API 보호 |
| **로그인** | RQ-LOGIN-020~021 | `POST /api/auth/login` | ✅ | PENDING → 401 + `ACCOUNT_PENDING` |
| **회원가입** | RQ-SIGNUP-* | signup · invite · username check | ✅ | Rate limit · StandardErrorResponse |
| **대시보드** | RQ-DSH-043 | `GET /api/v1/evidences/stats` | ✅ | 4카드 통계 (2026-06-17 반영) |
| **대시보드 차트** | RQ-DSH-044~045 | `stats/trend` · `stats/recent` | ✅ | 7일 트렌드 · 최근 위젯 (2026-06-18) |
| **분석 요청** | RQ-REQ-047~049 | upload · analyze · analysis-status | ✅ | **영상 MP4/MOV만** · RabbitMQ + Local worker |
| **무결성·CoC** | RQ-REQ-051, HIS-107 | CustodyLogs · `GET .../coc/verify` | ✅ | 증거별 체인 검증 API |
| **Recovery Score** | RQ-DTL-071~072 | `detail.integrityInfo` | ✅ | 메타데이터 기반 점수 |
| **WORM·S3** | RQ-REQ-048, SEC-150 | S3 upload (`original/`) | 🟡 | 코드 연동 있음 · Object Lock 운영은 INF |
| **X.509 사본 서명** | RQ-REQ-050, DTL-075~076 | 분석 copy Manifest + `signatureInfo` | ✅ | **단일 플랫폼 CA** (`Pkcs8ManifestSignatureService`) · PEM/Secrets Manager · INF 운영 CA 등록 🟡 |
| **보안 무결성 알림** | RQ-SEC-153, SK-632 | detail 자동검증 · `integrity/verify` | ✅ | 서명·CoC·블록체인 불일치 시 SECURITY_ALERT (2026-06-18) |
| **블록체인 앵커** | RQ-REQ-052, SEC-151~152 | `GET .../blockchain` | 🟡 | BE 파이프라인 ✅ · `hashValid`·`verificationMessage`·compare 해시 대조 · INF explorer URL 대기 |
| **분석 상세** | RQ-DTL-* | cases · evidence detail | ✅ | `moduleResults` · `evidenceInfo.caseId` · case evidences URL 필드(nullable) |
| **PDF 리포트** | RQ-DTL-084~087 | `GET .../reports/pdf` | ✅ | QR reportHash |
| **비교 검증** | RQ-CMP-* | `/api/v1/compare/**` | ✅ | originals · candidate · **`POST /cancel`** (FE 호환) |
| **분석 이력** | RQ-HIS-106 | `mypage/analysis-history` | ✅ | FE `CaseSummary` 사건 단위 집계 (2026-06-19) |
| **마이페이지** | RQ-MY-* | users/me · settings | ✅ | |
| **관리자** | RQ-ADMIN-* | `/api/v1/admin/**` | ✅ | `POST .../users/{id}/suspend` · 승인/반려/정지 `processedByUserId` |
| **알림** | RQ-COM-015~016 | `/api/v1/notifications` | ✅ | |
| **환경 설정** | RQ-COM-009 | `users/me/settings` | ✅ | |
| **성능 NFR** | RQ-PER-* | stats 30초 캐시 · trend 배치 쿼리 | 🟡 | 부하·실측(k6) 미실시 |

**범례:** ✅ 완료 · 🟡 부분 · ⬜ 미구현 · — 타 파트(FE/AI/INF) 주도

---

## 3. 백엔드 API 구현 목록 (2026-06-17)

### 3.1 Public / User

| Method | Path | 용도 |
| :--- | :--- | :--- |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/v1/auth/signup` | 회원가입 |
| GET | `/api/v1/auth/username/check` | 아이디 중복 |
| POST | `/api/v1/invite-codes/validate` | 초대코드 검증 |
| GET | `/api/v1/organizations/departments` | 부서 목록 |
| GET/PATCH | `/api/v1/users/me` | 프로필 |
| GET | `/api/v1/mypage/analysis-history` | 분석 이력 |
| GET | `/api/v1/cases/me` | 이력 alias |
| GET | `/api/v1/cases/{caseId}` | 사건 상세 |

### 3.2 Evidence / Analysis

| Method | Path | 용도 |
| :--- | :--- | :--- |
| GET | `/api/v1/evidences/stats` | 대시보드 통계 |
| GET | `/api/v1/evidences/stats/trend` | 7일 분석 추이 |
| GET | `/api/v1/evidences/stats/recent` | 최근 분석 위젯 |
| POST | `/api/v1/evidences/upload` | 업로드 + SHA-256 |
| POST | `/api/v1/evidences/analyze` | 분석 시작 |
| GET | `/api/v1/evidences/{id}/analysis-status` | 진행률 polling · **queuePosition/queueDepth** (QUEUED) |
| GET | `/api/v1/evidences/{id}/detail` | 증거 상세 (SEC-153 자동검증·알림, Recovery Score) |
| GET | `/api/v1/evidences/{id}/integrity/verify` | 무결성·서명·블록체인 검증 (SK-632) |
| GET | `/api/v1/evidences/{id}/coc/verify` | CoC 체인 검증 |
| DELETE | `/api/v1/evidences/{id}` | 업로드 취소 |
| DELETE | `/api/v1/evidences/{id}/reset` | 증거 초기화 |
| DELETE | `/api/v1/evidences/{id}/analysis` | 분석 중단 |

> **경로:** 위 표는 `/api/v1/evidences/**` 기준. `/api/evidences/*` legacy alias는 **제거됨** (2026-06). 로그인 `POST /api/auth/login`만 legacy 유지.

### 3.3 Admin (`ROLE_ADMIN`)

| Method | Path | 용도 |
| :--- | :--- | :--- |
| GET | `/api/v1/admin/dashboard/stats` | 관리자 대시보드 통계 (RQ-ADMIN-120) |
| GET | `/api/v1/admin/dashboard/analysis-stats` | 관리자 분석 통계 (RQ-ADMIN-150) |
| GET/PATCH/POST/DELETE | `/api/v1/admin/users/**` | 계정 관리 (`approve` · `reject` · **`suspend`**) |
| GET/POST | `/api/v1/admin/invite-codes/**` | 초대코드 |
| GET | `/api/v1/admin/logs` | 감사 로그 (+ CSV export) |
| GET/DELETE | `/api/v1/admin/evidences/**` | 증거 관리 |
| GET/PATCH | `/api/v1/admin/me/**` | 관리자 프로필 |
| POST | `/api/v1/admin/blockchain/merkle/anchor` | Merkle Root 수동 앵커 (WBS 2.10.2) |

**상세 스키마:** [api/specification.md §2](./api/specification.md)

### 3.4 Compare (`/api/v1/compare/**`)

| Method | Path | 용도 |
| :--- | :--- | :--- |
| GET | `/api/v1/compare/originals` | 비교용 원본 증거 목록·검색 (RQ-CMP-091) |
| GET | `/api/v1/compare/originals/{evidenceId}` | 원본 파일 기본정보 (SK-954) |
| POST | `/api/v1/compare/verify` | 비교 검증 실행 |
| POST | `/api/v1/compare/cancel` | 비교 검증 취소 (클라이언트 토큰, 204) |
| GET | `/api/v1/compare/{compareId}` | 비교 결과 조회 |
| GET | `/api/v1/compare/{compareId}/candidate` | 대조본 파일 기본정보 (SK-955) |
| GET | `/api/v1/compare/{compareId}/reports/pdf` | 비교 PDF (원본/대조본 섹션) |

---

## 4. 인프라·연동 (본 레포 관점)

| 연동 | 코드 | 운영 | 상태 |
| :--- | :---: | :---: | :---: |
| PostgreSQL / JPA | ✅ | 환경별 | ✅ |
| S3 업로드 | ✅ | 버킷·WORM | 🟡 |
| RabbitMQ 분석 큐 | ✅ | Broker | 🟡 |
| Local analysis worker (dev) | ✅ | — | ✅ |
| AI FastAPI worker | — | `ai/ai-forensic/` | 🟡 (타 레포) |
| Redis refresh token | ✅ (in-memory fallback) | Redis | 🟡 |
| Signup rate limit | In-memory | Redis | 🟡 |
| 블록체인 앵커 | ✅ simulated · http client | INF 게이트웨이 URL | 🟡 |
| Manifest X.509 서명 | ✅ PKCS#8 + X.509 | Secrets Manager `MANIFEST_SIGNING_SECRET_ID` | 🟡 |

---

## 5. 문서·개발 표준 (2026-06-18)

| 항목 | 상태 |
| :--- | :---: |
| AI 통합 진입점 [AGENTS.md](./AGENTS.md) | ✅ |
| 역할별 가이드 `teams/*` | ✅ |
| RQ 162건 [requirements/index.md](./requirements/index.md) | ✅ |
| Excel 정본 [requirements/source/](./requirements/source/) | ✅ |
| API 정본 + Gap [api/specification.md](./api/specification.md) | ✅ |
| 예외 통일 `BusinessException` + `StandardErrorResponse` | ✅ |
| Admin 페이지네이션 `content` / `totalElements` | ✅ |
| `.env` Git 추적 해제 + pre-commit 시크릿 차단 | ✅ |
| 구버전 md 정리 | ✅ |

---

## 6. RTM(추적 매트릭스) 주의

[requirements/traceability.md](./requirements/traceability.md)는 source Excel **백엔드 시트**에서 재생성됩니다.

- 구현 상태 ✅/🟡/⬜는 `api/specification.md`·코드와 **교차검토** 필요
- §2·§8과 `api/specification.md` §0.4를 **교차검토** — drift 있으면 §8 P0/P1 정리

**재생성:**

```bash
python scripts/extract_requirements_from_excel.py
python scripts/generate_requirements_markdown.py
```

---

## 7. 최근 완료 작업

### 2026-06-19 (Sprint 4·5 BE · 리팩토링)

| # | 작업 | 영향 |
| :---: | :--- | :--- |
| 1 | **analysis-status** `queuePosition` · `queueDepth` · stale job `ANALYSIS_TIMEOUT` | SK-473 / WBS 3.1.4 |
| 2 | **`FILE_TOO_LARGE` 413** · `AnalysisStaleJobReaper` 스케줄러 | SK-473 |
| 3 | **`POST /api/v1/admin/blockchain/merkle/anchor`** 수동 Merkle 앵커 | SK-468 |
| 4 | PDF **`REPORT_CREATED` / `REPORT_DOWNLOADED`** CoC | SK-486 / WBS 3.2.2 |
| 5 | 대시보드 **stats 30초 캐시** · trend **배치 쿼리** · 분석 완료 시 invalidate | SK-487 / RQ-PER-155 |
| 6 | `AnalysisQueueMetricsResolver` 등 서비스 분리 리팩토링 | 유지보수성 |
| 7 | `Sprint45E2EIntegrationTest` 등 테스트 추가 | 136 tests |

### 2026-06-19 (완료 WBS 감사 · BE 갭 보완)

| # | 작업 | 영향 |
| :---: | :--- | :--- |
| 1 | 상세 `moduleResults`에 **modelName · modelVersion · confidence** | SK-402 / DTL-082 |
| 2 | **`POST /api/v1/admin/users/{userId}/suspend`** + `processedByUserId` | SK-404 / SK-784 · SK-790 |
| 3 | Compare **originals · candidate** 조회 API 3종 | SK-465 / RQ-CMP-091 · SK-954 · SK-955 |
| 4 | Compare PDF **원본/대조본 기본정보** 섹션 | SK-466 / SK-988 · SK-986 |
| 5 | 블록체인 **`hashValid` · `verificationMessage` · compare 앵커 해시 대조** · explorer URL 템플릿 | SK-468 / DTL-079 · CMP-103 · DTL-080 |

### 2026-06-18 (`develop` → `main`)

| # | 작업 | 영향 |
| :---: | :--- | :--- |
| 1 | `feature/backendrule` → `develop` merge | API 정렬 · 문서 · 예외 통일 + develop CoC·분석중단 유지 |
| 2 | `.env` Git 추적 해제 · pre-commit 시크릿 차단 | 보안 · 재발 방지 |
| 3 | `develop` → `main` 릴리스 | 운영/배포 기준 브랜치 반영 |
| 4 | Excel 정본 `requirements/source/` 추가 | 명세 drift 추적 |

### 2026-06-17

| # | 작업 | 영향 |
| :---: | :--- | :--- |
| 1 | Evidence API `/api/v1/evidences` v1 only (legacy alias 제거) | FE 연동 경로 통일 |
| 2 | Stats → RQ-DSH-043 4카드 필드 | 대시보드 |
| 3 | Analyze `{ evidenceId }` + caseName 생략 | 명세 정렬 |
| 4 | Auth PENDING → 401 + errorCode | FE 분기 |
| 5 | 예외 Handler·JSON 단일화 | 전 API |
| 6 | Admin 페이지네이션 표준 | Admin FE |
| 7 | `docs/` AI 진입점·팀 가이드 | 협업 |

---

## 8. 미구현 · 리스크 (우선순위)

| 순위 | 항목 | RQ | 담당 | 리스크 |
| :---: | :--- | :--- | :--- | :--- |
| **P0** | AI 실연동 (result queue) | REQ-047~049 | BE + AI | mock/simulated만 검증 |
| **P1** | Manifest **운영 CA** Secrets Manager 등록 | REQ-050 | INF | BE 로더 ✅ · prod Secret/HSM 키 주입 대기 |
| **P1** | 블록체인 **http** 실연동 | SEC-151, REQ-052 | BE + INF | simulated만 동작 |
| **P2** | S3 Object Lock 운영 | REQ-048, SEC-150 | INF | 코드 업로드만 |
| **P2** | RabbitMQ·Redis 운영 | — | INF | local/simulated fallback |
| **P2** | 성능 NFR 검증 | PER-* | BE | 부하 테스트 미실시 |
| **P3** | Excel FE「호출 API」열 갱신 | — | PM | 명세·코드 drift |
| **P3** | traceability.md 재작성 | — | BE | 추적 신뢰도 |

---

## 9. 파트별 요약 (전체 프로젝트)

| 파트 | 레포 | 본 문서 기준 진행 | 다음 액션 |
| :--- | :--- | :--- | :--- |
| **백엔드** | `backend-forensic` | MVP API **~88%** · 운영 연동 **~35%** | AI 실연동 · INF Secret(CA) |
| **프론트엔드** | `frontend/frontend-deepfake/` | *(본 레포 미포함)* | stats·errorCode·content 필드 반영 |
| **AI** | `ai/ai-forensic/` | *(본 레포 미포함)* | ai-json 계약 · RabbitMQ 연동 |
| **인프라** | AWS/EKS | *(본 레포 미포함)* | S3 Object Lock · MQ · **Manifest signing Secret** |

---

## 10. 다음 스프린트 제안 (BE)

1. **FE·AI 실연동 E2E** — 업로드→S3→GPU→detail 전체 플로우 (BE API는 준비됨)  
2. **블록체인 http** — INF 게이트웨이 URL 수령 시 `BLOCKCHAIN_ANCHOR_MODE=http`  
3. **Manifest CA Secret** — INF가 `MANIFEST_SIGNING_SECRET_ID`에 운영 PKCS#8·X.509 등록  
4. **NFR 실측** — k6·응답 시간 측정 (캐시·쿼리 최적화는 반영 완료)  
5. **RTM 갱신** — traceability.md를 코드 기준으로 재작성  
6. ~~**레거시 alias** `/api/evidences` deprecation~~ → ✅ **완료** (v1 only, `EvidenceApiPaths.BASE`)

---

## 11. 관련 문서

| 문서 | 용도 |
| :--- | :--- |
| [AGENTS.md](./AGENTS.md) | AI·팀 통합 진입점 |
| [requirements/index.md](./requirements/index.md) | RQ 162건 |
| [api/specification.md](./api/specification.md) | API Gap · 정본 |
| [architecture/system-overview.md](./architecture/system-overview.md) | 시스템 흐름 |

---

## 12. 변경 이력

| 날짜 | 작성자 | 내용 |
| :--- | :--- | :--- |
| 2026-06-19 | — | **FE develop 연동:** analysis-history `CaseSummary` 사건 집계 · compare `POST /cancel` · detail `caseId` · case evidences URL 필드 · `CaseKeyNormalizer` 한글 caseKey · [api/specification.md v1.7](./api/specification.md) (138 tests) |
| 2026-06-19 | — | **Sprint 4·5 BE:** queuePosition/depth · stale reaper · FILE_TOO_LARGE 413 · admin Merkle 앵커 · PDF CoC · dashboard 캐시 · §1·§2·§3·§7·§10·§12 갱신 (136 tests) |
| 2026-06-19 | — | **완료 WBS 18건 감사 후 BE 갭 보완:** detail `moduleResults` modelName/modelVersion/confidence · admin **suspend** API + `processedByUserId` · compare **originals/candidate** API · compare PDF 원본/대조본 섹션 · §2·§3.4·§7 갱신 |
| 2026-06-19 | — | 블록체인 상세 **`hashValid`/`verificationMessage`** · compare **BLOCKCHAIN_HASH** 앵커 해시 대조 · **`transactionExplorerUrl`** 템플릿 (`blockchain.anchor.explorer-url-template`) |
| 2026-06-19 | — | X.509 mock 제거 → **단일 플랫폼 CA** (`Pkcs8ManifestSignatureService`) · PEM/Secrets Manager · §1·§2·§4·§8·§9·§10 갱신 |
| 2026-06-19 | — | `main` PR #25 반영 · §1·§8 outdated(PDF/Compare) 정리 · X.509 mock 상태 명시 |
| 2026-06-18 | — | RQ-SEC-153/SK-632: detail 자동 무결성 검증·`SECURITY_ALERT` · `GET .../integrity/verify` |
| 2026-06-17 | — | Recovery Score(DTL-071~072) · CoC 검증 API(HIS-107) · 문서 Gap 갱신 |
| 2026-06-18 | — | source Excel → index/traceability 재생성 · **영상-only** 스코프 반영 (docs·코드) |
| 2026-06-17 | — | 초판 — 코드·문서·Gap 분석 기반 진행 상황 |
