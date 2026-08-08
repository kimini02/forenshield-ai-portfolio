# ForenShield AI — Frontend (frontend-forensic)

딥페이크 포렌식 분석 플랫폼 **ForenShield AI**의 프론트엔드입니다.

이번 단계의 목적은 화면을 완성하는 것이 아니라, **팀원들이 각자 맡은 페이지를 독립적으로 개발할 수 있도록**
라우트 폴더 / 공통 컴포넌트 / 공통 스타일을 나누는 것입니다.

> - 메인 페이지(`/main`)는 기존에 만들어 둔 디자인을 그대로 사용합니다.
> - 나머지 페이지는 동일한 디자인 언어의 **기본 화면(뼈대)** 만 구현되어 있으며, 디자인은 각 담당자가 자유롭게 발전시킵니다.
> - 백엔드 API 연동, 실제 인증(로그인/JWT), 딥페이크 분석 기능은 **추후 진행 예정**입니다. (현재는 더미 데이터)

## 기술 스택

- **Next.js 16** (App Router) + React 19
- **Tailwind CSS v4** + shadcn/ui (base-nova, @base-ui/react)
- lucide-react (아이콘)

## 실행 방법

```bash
# 1) 의존성 설치
pnpm install

# 2) 개발 서버 실행
pnpm dev
```

실행 후 브라우저에서 다음 경로로 접근할 수 있습니다. (`/` 접속 시 `/main`으로 이동)

| 경로 | 화면 | 담당자 |
|---|---|---|
| `/main` | 메인 (파일 업로드 / 분석 요청) | 이새연 |
| `/login` | 로그인 | 장현준 |
| `/signup` | 회원가입 | 김민희 |
| `/mypage` | 마이페이지 (분석 기록) | 윤형진 |
| `/admin` | 관리자 (가입 승인 / CoC 로그) | 나중 |

## 폴더 구조

```
frontend-forensic/
├── app/
│   ├── layout.tsx           # 루트 레이아웃 (폰트/메타)
│   ├── page.tsx             # / → /main 리다이렉트
│   ├── globals.css          # Tailwind 테마 / 디자인 토큰
│   ├── main/page.tsx        # 이새연 — 메인 (기존 디자인)
│   ├── login/page.tsx       # 장현준 — 로그인
│   ├── signup/page.tsx      # 김민희 — 회원가입
│   ├── mypage/page.tsx      # 윤형진 — 마이페이지
│   └── admin/page.tsx       # 나중 — 관리자
├── components/
│   ├── site-header.tsx      # 공통 상단 네비게이션
│   ├── upload-panel.tsx     # 메인 — 업로드 패널
│   ├── capabilities-section.tsx
│   ├── recent-analyses.tsx
│   └── ui/                  # shadcn 공통 컴포넌트
├── lib/utils.ts
├── docs/
│   └── FRONTEND_ROLE_ASSIGNMENT.md
├── package.json
└── next.config.mjs
```

자세한 역할 분담은 [`docs/FRONTEND_ROLE_ASSIGNMENT.md`](./docs/FRONTEND_ROLE_ASSIGNMENT.md)를 참고하세요.

## 현재 범위 / 추후 예정

- ✅ 라우트 구조, 공통 헤더/스타일, 페이지별 기본 화면
- ✅ 메인 디자인(업로드/분석 역량/최근 분석) — 더미 데이터
- ⏳ 백엔드 API 연동 (추후)
- ⏳ 실제 로그인 / JWT 인증 (추후)
- ⏳ 실제 파일 업로드 및 딥페이크 분석 (추후)
