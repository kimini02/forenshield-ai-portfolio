# 요구사항·기능 명세 개요

> **원본 Excel (정본):** `docs/requirements/source/`  
> - `요구사항명세서_최종 (1).xlsx` — **RQ 169건** (자동 추출)  
> - `기능명세서_최종.xlsx` — **FN** FE/BE/AI/INF 시트  
> **지원 미디어:** **영상(VIDEO)만** — 음성·이미지 제외

본 폴더의 Markdown은 Excel에서 추출·정리한 **검색용 색인**입니다. Excel과 Markdown이 다르면 **Excel을 따릅니다**.

---

## 1. RQ vs FN

| 구분 | ID 형식 | 의미 | 문서 |
| :--- | :--- | :--- | :--- |
| **RQ** (Requirements) | `RQ-{영역}-{번호}` | 사용자·비즈니스 **요구사항** (무엇을 해야 하는가) | [index.md](./index.md) |
| **FN** (Functions) | `FN-{영역}-{번호}-{파트}` | 파트별 **구현 기능** (누가 무엇을 만드는가) | [traceability.md](./traceability.md) |

파트 접미사: `FE` · `BE` · `AI` · `INF`

---

## 2. RQ 영역 지도

| 영역 코드 | 화면/기능 | RQ 건수(대략) | 주요 API·문서 |
| :--- | :--- | :---: | :--- |
| `COM` | 공통 UI·헤더·알림 | ~16 | FE 라우트 가드 |
| `LOGIN` | 로그인 | ~12 | `POST /api/auth/login` |
| `SIGNUP` | 회원가입 | ~9 | [signup.md](../api/signup.md) |
| `DSH` | 메인 대시보드 | ~5 | `GET /api/v1/evidences/stats` |
| `REQ` | 분석 요청·업로드 | ~6 | `POST .../upload` (**영상 MP4/MOV**), `.../analyze` |
| `DTL` | 분석 상세 | ~38 | `GET /api/v1/cases/{id}`, evidence detail |
| `CMP` | 비교 검증 | ~13 | ⬜ API 미구현 |
| `HIS` | 분석 이력 | ~2 | `GET /api/v1/mypage/analysis-history` |
| `MY` | 마이페이지 | ~6 | `GET/PATCH /api/v1/users/me` |
| `ADMIN` | 관리자 | ~36 | `/api/v1/admin/**` |
| `SEC` | 보안·블록체인 | ~4 | S3 WORM · CoC · [s3.md](../integrations/s3.md) |
| `PER` | 성능 | ~3 | NFR |
| `NFR` | 비기능 공통 | ~19 | JWT · 권한 · 로깅 |

**전문 목록:** [index.md](./index.md) (영역별 표)

---

## 3. 기능명세서 Excel 시트 (참고)

기능명세서 Excel은 보통 아래 시트로 구성됩니다 (팀 공유본 기준):

| 시트 | 내용 | AI가 볼 때 |
| :--- | :--- | :--- |
| 요구사항 매핑 | RQ ↔ FN 연결 | traceability 보완 |
| FE | 화면별 UI·**호출 API** | [teams/frontend.md](../teams/frontend.md) + [specification.md](../api/specification.md) |
| BE | API·Service·DB | [teams/backend.md](../teams/backend.md) |
| AI | 모델·입출력 | [integrations/ai-json.md](../integrations/ai-json.md) |
| INF | S3·MQ·배포 | [teams/infrastructure.md](../teams/infrastructure.md) |
| UI상세 | 컴포넌트·필드 | FE 구현 시 Excel 직접 참조 권장 |

Markdown에는 **RQ 전문**과 **BE RTM**이 포함되어 있습니다. FE/AI/INF FN 전체 266건은 Excel 또는 traceability 확장이 필요합니다.

---

## 4. 구현 추적 (RTM)

[traceability.md](./traceability.md) — RQ ↔ FN ↔ API ↔ 구현 상태 (✅ / ⬜ / FE 담당)

**상태 emoji**

| 표시 | 의미 |
| :---: | :--- |
| ✅ | 백엔드 구현 완료 |
| ⬜ | 미구현 |
| — (FE 담당) | UI/라우트는 프론트 책임 |

---

## 5. Excel 갱신 → Markdown 재생성

1. Excel 수정 후 `docs/requirements/source/` 저장  
2. `python scripts/extract_requirements_from_excel.py`  
3. `python scripts/generate_requirements_markdown.py` → `index.md` · `traceability.md`  
4. API/DB 변경 시 [specification.md](../api/specification.md) · [erd.md](../database/erd.md) 동시 수정  

---

## 6. AI 에이전트 사용법

1. 작업 티켓의 **RQ-ID** 확인 (예: `RQ-DSH-043`)  
2. [index.md](./index.md)에서 해당 행 검색 → 요구사항 **내용·중요도** 파악  
3. [traceability.md](./traceability.md)에서 본인 파트 FN-ID 확인  
4. API/DB는 [specification.md](../api/specification.md) · [erd.md](../database/erd.md)에서 **추측하지 말고** 확인

---

## 7. 관련 문서

- [../AGENTS.md](../AGENTS.md) — 통합 진입점  
- [../rule.md](../rule.md) §3 — ID 체계 상세  
- [../api/specification.md §0](../api/specification.md) — 명세 vs 코드 Gap
