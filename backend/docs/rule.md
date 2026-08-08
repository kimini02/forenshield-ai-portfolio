# ForenShield AI 팀 개발 규칙

> **문서 버전:** v1.1  
> **기준 명세:** `요구사항명세서_최종 (1).xlsx` · `기능명세서_최종.xlsx`  
> **적용 대상:** 프론트엔드 · 백엔드 · AI · 인프라 전 파트

본 문서는 **프로젝트 헌법**입니다. 모든 팀원과 AI 어시스턴트는 아래 규칙과 연결된 세부 문서를 기준으로 작업합니다.  
**주먹구구식 구현을 금지**하며, RQ-ID / FN-ID 없이 기능을 추가하지 않습니다.

---

## 1. 프로젝트 개요

| 항목 | 내용 |
| :--- | :--- |
| **서비스명** | ForenShield AI |
| **목적** | 내부망 전용 디지털 포렌식 **영상** 증거 무결성 검증 및 AI 딥페이크 분석 |
| **총 요구사항** | RQ **169건** (Excel 자동 추출) |
| **총 기능(FN)** | 266건 (파트별: FE / BE / AI / INF) |

### 1.1 레포지토리·기술 스택

| 파트 | 기술 스택 | 경로 |
| :--- | :--- | :--- |
| **프론트엔드** | Next.js 16 · React 19 · Tailwind v4 · shadcn/ui | `frontend/frontend-deepfake/` |
| **백엔드** | Spring Boot 3 · JPA · PostgreSQL | `backend/backend-forensic/` (본 레포) |
| **AI** | FastAPI · Celery Worker | `ai/ai-forensic/` |
| **인프라** | AWS S3 Object Lock · Redis · RabbitMQ · 블록체인 앵커 · HTTPS | AWS / EKS |

---

## 2. 문서 체계 (필독)

**AI·전 파트 진입점:** [AGENTS.md](./AGENTS.md) — 다른 AI 에이전트에게 **이 파일부터** 첨부하세요.

**사람용 색인:** [README.md](./README.md)

| 구분 | 문서 | 용도 |
| :--- | :--- | :--- |
| **협업** | [AGENTS.md](./AGENTS.md) | AI·전 파트 통합 진입점 |
| **협업** | [rule.md](./rule.md) | 브랜치·커밋·PR·RQ/FN |
| **절차** | [guides/development.md](./guides/development.md) | 작업 체크리스트 |
| **구현 표준** | [guides/implementation-standards.md](./guides/implementation-standards.md) | API 응답·예외·FE 계약 |
| **명명** | [guides/naming.md](./guides/naming.md) | 코드·DB·Git |
| **요구사항** | [requirements/index.md](./requirements/index.md) | RQ 162건 |
| **추적** | [requirements/traceability.md](./requirements/traceability.md) | RTM |
| **API** | [api/specification.md](./api/specification.md) | **API 정본 + Gap 분석** |
| **API 규칙** | [api/convention.md](./api/convention.md) | URL·errorCode |
| **가입 API** | [api/signup.md](./api/signup.md) | 회원가입 상세 |
| **DB** | [database/erd.md](./database/erd.md) | ERD v3 |
| **연동** | [integrations/](./integrations/) | S3 · RabbitMQ · AI JSON |

> **원본 Excel:** `요구사항명세서_최종 (1).xlsx`, `기능명세서_최종.xlsx`  
> 충돌 시: **Excel → ERD → 코드** 순 협의.

---

## 3. ID 체계 (RQ / FN)

### 3.1 요구사항 ID (RQ)

```
RQ-{영역}-{번호}
```

| 접두 | 영역 | 예시 |
| :--- | :--- | :--- |
| `COM` | 공통 UI/헤더 | RQ-COM-001 |
| `LOGIN` | 로그인 | RQ-LOGIN-020 |
| `SIGNUP` | 회원가입 | RQ-SIGNUP-037 |
| `DSH` | 대시보드 | RQ-DSH-043 |
| `REQ` | 분석 요청 | RQ-REQ-048 |
| `DTL` | 분석 상세 | RQ-DTL-057 |
| `HIS` | 분석 이력 | — |
| `CMP` | 비교 검증 | — |
| `MYP` | 마이페이지 | — |
| `ADM` | 관리자 | — |
| `NFR` | 비기능 | RQ-NFR-160 |

**중요도:** 상 · 중 · 하 (명세서 기준)

### 3.2 기능 ID (FN)

```
FN-{영역}-{RQ번호}-{파트접미}
```

| 접미 | 담당 |
| :--- | :--- |
| `FE` | 프론트엔드 |
| `BE` | 백엔드 |
| `AI` | AI 워커 |
| `INF` | 인프라 |

**예:** `RQ-LOGIN-020` → `FN-LOGIN-020-BE` (로그인 API), `FN-LOGIN-020-FE` (로그인 화면)

### 3.3 작업 시 필수 표기

- PR 제목·설명에 **RQ-ID** 와 본인 파트 **FN-ID** 를 명시합니다.
- 커밋 본문(선택)에도 `Refs: RQ-REQ-048, FN-REQ-048-BE` 형식으로 추적 가능하게 남깁니다.

---

## 4. 브랜치 전략

| 브랜치 | 목적 | 병합 주기 |
| :--- | :--- | :--- |
| **`main`** | 운영 배포용 (안정) | 주 1회 |
| **`develop`** | 개발 통합 | 일 1회 |
| **`feature/{영역}-{요약}`** | 기능 개발 | 수시 |

**브랜치 명명 예**

```
feature/be-evidence-upload
feature/fe-login-session
feature/ai-video-worker
```

- `feature/` 브랜치는 **`develop`에서 분기** → 완료 후 **`develop`으로 PR**
- hotfix는 `hotfix/{이슈}` → `main` + `develop` 양쪽 반영 (팀 합의)

---

## 5. 커밋 메시지 규칙

[Conventional Commits](https://www.conventionalcommits.org/) 말머리를 **필수** 사용합니다.

| 말머리 | 용도 |
| :--- | :--- |
| `feat:` | 새 기능 (RQ/FN 연계) |
| `fix:` | 버그 수정 |
| `docs:` | 문서만 변경 |
| `style:` | 포맷 (로직 변경 없음) |
| `refactor:` | 리팩터링 |
| `test:` | 테스트 추가·수정 |
| `chore:` | 빌드·의존성·CI |

**예시**

```
feat: add evidence upload SHA-256 and S3 WORM storage

Refs: RQ-REQ-048, FN-REQ-048-BE
```

---

## 6. Pull Request 규칙

1. **시점:** 기능 단위 완료 시 `develop`으로 PR. 가능하면 **매일 17:00** 전후 통합.
2. **승인:** 동료 **1명 이상 Approve** 후 Merge (본인 Merge 가능).
3. **PR 템플릿 항목 (필수)**
   - 연결 RQ-ID / FN-ID
   - 변경 요약 (3줄 이내)
   - API 변경 여부 (변경 시 `api/specification.md` 갱신 링크)
   - 테스트 방법 (`./gradlew test` 등)
4. **충돌:** PR 작성자가 직접 해결.
5. **문서 동반:** API·DB·메시지 규격 변경 시 **같은 PR**에서 Markdown 갱신.

---

## 7. 파트별 책임 경계

| 변경 유형 | 담당 | 협의 필요 |
| :--- | :--- | :--- |
| UI·라우팅·sessionStorage | FE | BE API 계약 |
| REST API·DB·CoC 로그 | BE | ERD, AI JSON |
| 모델 추론·Celery/RabbitMQ | AI | integrations/ai-json |
| S3·Redis·TLS·블록체인 | INF | BE 연동 지점 |

**금지 사항**

- FE에서 `role`·`status` 등 권한 필드를 임의 설정
- BE에서 명세 없는 API 경로 추가 (`/api/v1` 규칙 위반)
- AI 결과 JSON 필드명 임의 변경 (프론트·BE 동시 깨짐)

---

## 8. 구현 우선순위 (MVP)

명세 **중요도 `상`** RQ를 먼저 완료합니다. RTM의 ⬜ 미구현 항목 중 MVP 필수:

1. 인증·가입·관리자 승인 (`LOGIN`, `SIGNUP`, `ADM`)
2. 증거 업로드 · SHA-256 · S3 WORM (`REQ-047`~`048`)
3. 분석 요청 · 비동기 큐 · 결과 저장 (`REQ-049`, `DTL`)
4. CoC 해시 체인 (`REQ-051`, `DTL-077`)
5. JWT·관리자 API 권한 (`NFR-160`~`162`)

**MVP 이후:** 블록체인 앵커(`REQ-052`), X.509 사본 서명(`REQ-050`), PDF 보고서 등

---

## 9. 로컬 개발·검증

```bash
# 백엔드 (본 레포)
./gradlew test
./gradlew bootRun

# Swagger (로컬)
http://localhost:8080/swagger-ui.html
```

- PR 전 **`./gradlew test` 통과** 필수
- API 추가 시 **Controller 테스트** 또는 통합 테스트 1건 이상
- `.env`·시크릿 **커밋 금지**

---

## 10. AI 어시스턴트 지침

- 본 파일(`docs/rule.md`)과 [api/convention.md](./api/convention.md), [database/erd.md](./database/erd.md)를 최우선 컨텍스트로 사용합니다.
- 코드 생성 시 **RQ-ID / FN-ID**를 주석 또는 PR 설명에 남깁니다.
- API 경로는 [api/convention.md](./api/convention.md)의 Base URL 규칙을 따릅니다.
- 불명확한 요구는 Excel 명세·RTM을 확인한 뒤 질문합니다. 추측 구현을 하지 않습니다.

---

## 11. 변경 이력

| 날짜 | 버전 | 내용 |
| :--- | :--- | :--- |
| 2026-06-17 | v1.2 | docs 폴더 주제별 정리, 중복·임시 파일 제거 |
| 2026-06-17 | v1.1 | Excel 최종 명세 반영, 문서 체계·RTM·API 규칙 정립 |
| (이전) | v1.0 | 브랜치·커밋·PR 기본 규칙 |
