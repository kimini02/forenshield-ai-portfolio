# 프론트엔드 역할 분담 (ForenShield AI)

각자 맡은 페이지(라우트 폴더) 안에서 독립적으로 작업합니다.
메인 페이지는 기존 디자인을 기반으로 하며, 디자인은 각 담당자가 자유롭게 발전시킵니다.

## 역할 분담표

| 담당자 | 담당 페이지 | 경로 | 작업 폴더(파일) | 주요 작업 |
|---|---|---|---|---|
| 장현준 | 로그인 | `/login` | `app/login/page.tsx` | 로그인 UI / (추후) API 연동 |
| 윤형진 | 마이페이지 | `/mypage` | `app/mypage/page.tsx` | 분석 기록 조회 UI |
| 이새연 | 메인페이지 | `/main` | `app/main/page.tsx` | 파일 업로드 및 분석 요청 UI |
| 김민희 | 회원가입 | `/signup` | `app/signup/page.tsx` | 회원가입 및 승인 대기 UI |
| 나중 | 관리자 | `/admin` | `app/admin/page.tsx` | 가입 승인 및 CoC 로그 UI |

## 작업 규칙

- 본인 페이지 라우트 폴더(`app/<페이지>/`) 안에서만 작업하면 파일 충돌이 거의 없습니다.
- 메인 페이지(`app/main/`)는 `components/`의 컴포넌트들을 사용합니다.
  - `site-header.tsx`, `upload-panel.tsx`, `capabilities-section.tsx`, `recent-analyses.tsx`
- 공통으로 수정이 필요한 파일(아래)은 변경 전 팀과 공유해 주세요.
  - `app/layout.tsx`, `app/page.tsx`(루트 → /main 리다이렉트)
  - `components/site-header.tsx` (상단 공통 네비게이션)
  - `components/ui/*` (shadcn 공통 컴포넌트)
  - `app/globals.css` (Tailwind 테마/디자인 토큰)
- 스타일은 **Tailwind v4 + shadcn(base-nova)** 를 사용합니다. 색상/토큰은 `app/globals.css`에 정의되어 있습니다.
- API는 현재 연동되어 있지 않습니다(더미 데이터). 실제 연동은 백엔드 준비 후 진행 예정입니다.
