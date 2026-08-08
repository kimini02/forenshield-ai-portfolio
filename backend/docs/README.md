# ForenShield 문서 (docs/)

> **진행 상황:** **[PROJECT_STATUS.md](./PROJECT_STATUS.md)** · **AI 진입점:** [AGENTS.md](./AGENTS.md)  
> **배포 기준 브랜치:** `main` (2026-06-18 — `develop` 머지 반영)

---

## 폴더 구조

```
docs/
├── AGENTS.md               ← ★ AI·전 파트 통합 진입점 (필독)
├── PROJECT_STATUS.md       ← ★ 진행 상황·Gap·다음 스프린트
├── README.md               ← 이 파일
├── rule.md                 ← 협업 헌법
│
├── architecture/           ← 시스템 전체
│   └── system-overview.md
├── product/
│   └── domain-glossary.md  ← 용어·Enum
├── teams/                  ← 파트별 온보딩
│   ├── frontend.md
│   ├── backend.md
│   ├── ai.md
│   └── infrastructure.md
│
├── guides/                 ← 개발 방법
│   ├── development.md
│   ├── implementation-standards.md
│   └── naming.md
│
├── requirements/           ← Excel 명세 (Markdown + 원본)
│   ├── overview.md         ← RQ/FN 체계·영역 지도
│   ├── index.md            ← RQ 169건 전문 (Excel 추출)
│   ├── traceability.md     ← RQ ↔ FN ↔ 구현 RTM
│   └── source/             ← ★ Excel 정본 (.xlsx)
│
├── api/                    ← REST API
│   ├── specification.md    ← API 정본 + Gap
│   ├── convention.md
│   └── signup.md
│
├── database/
│   └── erd.md
│
├── integrations/
│   ├── s3.md
│   ├── rabbitmq.md
│   └── ai-json.md
│
└── scripts/                ← Excel → Markdown 재생성 (선택)
    └── README.md
```

---

## 역할별 빠른 링크

| 역할 | 시작 문서 |
| :--- | :--- |
| **모든 AI / 신규 팀원** | [AGENTS.md](./AGENTS.md) → [PROJECT_STATUS.md](./PROJECT_STATUS.md) |
| 프론트엔드 | [teams/frontend.md](./teams/frontend.md) |
| 백엔드 | [teams/backend.md](./teams/backend.md) |
| AI | [teams/ai.md](./teams/ai.md) |
| 인프라 | [teams/infrastructure.md](./teams/infrastructure.md) |

---

## 무엇을 찾을 때

| 하고 싶은 일 | 읽을 문서 |
| :--- | :--- |
| **전체 진행률·다음 작업** | [PROJECT_STATUS.md](./PROJECT_STATUS.md) |
| 프로젝트 전체 파악 (AI용) | [AGENTS.md](./AGENTS.md) |
| 시스템 흐름·다이어그램 | [architecture/system-overview.md](./architecture/system-overview.md) |
| Evidence·Case·CoC 용어 | [product/domain-glossary.md](./product/domain-glossary.md) |
| RQ-ID 내용 | [requirements/index.md](./requirements/index.md) |
| RQ/FN 구조 이해 | [requirements/overview.md](./requirements/overview.md) |
| 구현 했는지 추적 | [requirements/traceability.md](./requirements/traceability.md) |
| API 경로·Body | [api/specification.md §2](./api/specification.md) |
| errorCode·예외 JSON | [guides/implementation-standards.md](./guides/implementation-standards.md) |
| PDF·공개 검증 단계별 개선 | [guides/report-pdf-public-verification-phase-plan.md](./guides/report-pdf-public-verification-phase-plan.md) |
| DB 테이블 | [database/erd.md](./database/erd.md) |
| BE↔AI JSON | [integrations/ai-json.md](./integrations/ai-json.md) |

---

## 원본 Excel (정본)

레포 내 경로: **`requirements/source/`**

| 파일 | 용도 | Markdown 색인 |
| :--- | :--- | :--- |
| `기능명세서_최종.xlsx` | FN 266건 · FE/BE/AI/INF 시트 | [traceability.md](./requirements/traceability.md) |
| `기능명세서_검토수정.xlsx` | 검토·수정본 (참고) | — |
| `요구사항명세서_최종 (1).xlsx` | RQ **169건** | [index.md](./requirements/index.md) |

충돌 시: **Excel → ERD → API 문서 → 코드**

**지원 미디어:** 업로드·AI 분석은 **영상(VIDEO, MP4/MOV)만** — 음성·이미지 제외. Excel 재생성: [scripts/README.md](./scripts/README.md)

---

## Git 브랜치 (백엔드)

| 브랜치 | 용도 |
| :--- | :--- |
| `main` | **배포·릴리스 기준** (2026-06-18 `develop` 반영) |
| `develop` | 통합·스프린트 merge 대상 |
| `feature/*` | 기능 단위 PR |

시크릿: `.env`·PEM·`config/*secrets*.env`는 **커밋 금지** · `.env.example`만 버전 관리 · clone 후 `pwsh ./scripts/git/install-hooks.ps1` (또는 `./scripts/git/install-hooks.sh`) · CI `secret-scan.yml`

---

## 작성·갱신 원칙

1. RQ/FN 없이 기능 추가 금지  
2. API 변경 → `api/specification.md` + `api/convention.md`  
3. DB 변경 → `database/erd.md` 먼저  
4. BE↔AI JSON 변경 → `integrations/ai-json.md`  
5. PR에 RQ-ID / FN-ID 명시  
6. 마일스톤 종료 시 [PROJECT_STATUS.md](./PROJECT_STATUS.md) §7·§12 갱신  

---

## 팀 진행 상태 (2026-07-14)

| 항목 | 상태 |
| :--- | :---: |
| 문서 구조·AI 진입점 | ✅ |
| API v1·stats·analyze·auth errorCode | ✅ |
| 예외 Handler 통일 (`BusinessException`) | ✅ |
| Admin 페이지네이션 표준 | ✅ |
| `.env` 추적 해제 · pre-commit | ✅ |
| `develop` → `main` 릴리스 | ✅ |
| Excel source (`requirements/source/`) | ✅ RQ·FN xlsx · 재생성 스크립트 |
| PDF / Compare / Notifications / Settings | ✅ | [api/specification.md §6](./api/specification.md) |
| RBAC 검토 플로우 · HLS · step-up | ✅ |

**BE RQ 진행:** 핵심 MVP·고급 REST(PDF·Compare·알림·검토) ✅ · 블록체인 운영 환경 의존 🟡 — 상세는 [PROJECT_STATUS.md](./PROJECT_STATUS.md) · [api/specification.md](./api/specification.md)
