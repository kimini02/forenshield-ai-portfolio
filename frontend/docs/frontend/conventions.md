# 프론트엔드 코드 컨벤션 (frontend-forensic)

> 코드 추가/수정 전에 이 문서를 먼저 읽으세요.
> 목표: **처음 보는 사람이 30초 안에 데이터 흐름·상태·실패 지점을 이해**할 수 있게.
> 재사용성보다 **정확성 → 보안 → 이해 가능성**이 우선입니다.

---

## 1. 코드 스타일 (표면)

- **세미콜론 없음**, **큰따옴표**, 2-space 들여쓰기
- 파일명 **kebab-case** (`evidence-summary-card.tsx`), 컴포넌트 **PascalCase** (`EvidenceSummaryCard`)
- 컴포넌트는 **named export** (`export function Foo`), `page.tsx`만 `export default`
- import 순서: `react`/`next` → 아이콘(lucide) → `@/app/...` → `@/components/...` → `@/lib/...` → 마지막 `cn`
- 경로는 항상 `@/` 별칭, 타입만 가져올 땐 `import type`
- `"use client"`는 **hook/브라우저 API/이벤트 핸들러를 직접 쓰는 파일에만** (서버컴포넌트 기본 유지)
- 주석은 한국어로 간결하게, "왜"를 적기

## 2. API 호출

- **표준 클라이언트 하나만 사용**: `@/lib/api/client`
  - `apiRequest` (JSON) · `apiRequestForm` (multipart) · `apiDownload` (Blob) · `ApiError`
- 새 API는 `lib/api/<도메인>.ts`에 함수로 추가 (화면에서 `fetch` 직접 호출 금지)
- **body는 객체로** 넘긴다 (내부에서 JSON.stringify). `body: JSON.stringify(x)` 금지
- **공개 엔드포인트**(로그인/회원가입/아이디중복/초대코드/부서)는 `{ auth: false }`
- 에러 처리는 `@/lib/api/errors`의 `getApiErrorMessage(e)` / `isUnauthorizedError(e)` 사용

```ts
import { apiRequest } from "@/lib/api/client"
export function fetchFoo(): Promise<Foo> {
  return apiRequest<Foo>("/api/v1/foo")
}
```

## 3. 인증

- 액세스 토큰은 **메모리만**(`lib/auth.ts`). **localStorage/sessionStorage에 토큰 저장 금지** (XSS 노출)
- refresh는 **HttpOnly 쿠키**로만, 요청엔 `credentials: "include"` (표준 클라가 처리)
- 로그인 응답 처리는 `applyLoginResponse(response)` 사용
- 앱 시작 시 세션 복구는 `AuthProvider`(layout)가 담당 → 화면에서 다시 만들지 말기
- 401은 클라이언트 인터셉터가 refresh→재시도→실패 시 `/login` 처리. 화면에서 중복 처리 X

## 4. mock / real 경계 (중요)

- mock은 **`features.mockApi`일 때만** (`@/lib/features`). 환경변수 직접 읽기 금지
- **real 모드에서 가짜 결과 금지** — API 실패를 mock으로 대체하거나 `riskScore`로 프레임/근거를 지어내지 않는다
- 결과가 없으면 상태 UI로: **`분석 대기` / `분석 중` / `분석 근거 없음` / `현재 AI 분석 결과를 사용할 수 없습니다`**
  - "근거 없음"을 "정상/원본/딥페이크 아님"으로 단정하지 말 것
- mock 생성기는 `lib/mock/` 또는 route-local `_mock/`에만. **화면 컴포넌트에서 mock 생성기 직접 import 금지**

## 5. 상태(state) 최소화

- **계산 가능한 값은 상태로 저장하지 말고 렌더에서 계산** (파생 상태 금지)
- `useEffect`는 **외부 시스템 동기화에만** (구독/타이머/직접 네트워크) + 반드시 cleanup/AbortController
- `const fullName = ...` 로 될 걸 `useEffect`+`setState`로 만들지 않기
- 상태 위치: 입력/모달 = 로컬 · 검색/정렬/페이지 = (공유 필요 시) URL · 서버데이터 = 서버상태 · 로그인/테마 = 제한적 전역
- 데이터 화면은 **loading / error / empty / success** 4상태를 명확히

## 6. 컴포넌트 구조

- `page.tsx`는 **데이터 로딩 + 화면 조립만**. 세부 UI는 분리
- 한 화면 전용 → `app/<route>/_components`, `_hooks`, `_lib`
- 여러 화면 공유 → `components/`, 로직/도메인 → `lib/`
- 분리 순서: **타입/상수/순수함수 → props-only 표현 컴포넌트 → 상태UI(empty/error) → (필요 시) hook**
- 파일은 가능하면 500줄 이하 목표. 단 **줄 수 맞추려는 억지 분리 금지** (응집도 우선)
- `features/` 식 통째 재구조화는 지금 규모엔 오버 — route-local 유지

## 7. 포맷/라벨 공통화

중복으로 만들지 말고 가져다 쓰기:
- 날짜/파일크기/길이 → `@/lib/formatters` (`formatDateTime`, `formatFileSize`, `formatDuration`)
- 분석 상태/위험도 라벨 → `@/lib/status-labels`
- feature flag → `@/lib/features`
- 위험도 기준선: **70 이상 위험 · 40~69 주의 · 그 미만 정상**

## 8. 디자인 / 스타일

- **디자인 토큰만 사용**: `bg-card` `border-border` `text-foreground` `text-muted-foreground` 등. 임의 색 신설 금지
- **다크모드 필수**: 밝은 색(`bg-white`, `text-slate-900` 등)엔 반드시 `dark:` 변형
- 강조 컬러 = teal (`teal-600`), 조건부 클래스는 `cn()`
- 카드 `rounded-xl border border-border bg-card`, 아이콘 `size-4`(강조 `size-5`)
- 아이콘 `aria-hidden="true"`, 의미 있는 건 `aria-label`/sr-only

## 9. 접근성 (기본)

- 클릭 가능한 `div` 대신 `button`, 입력엔 `label`, 키보드로 모든 기능 사용 가능
- 색만으로 상태 구분하지 않기, 포커스 표시 유지

## 10. 보안 (프론트가 지킬 것)

- 토큰 storage 저장 금지(메모리만), 비밀값/API 키를 `NEXT_PUBLIC_*`·번들에 넣지 않기
- 외부 링크엔 `rel="noopener noreferrer"`
- 검증 안 된 값 `dangerouslySetInnerHTML` 금지 (백엔드/CMS 데이터도 신뢰 X)
- **버튼 숨김 = 권한 아님.** 인가/CSRF/파일검증은 백엔드 책임 — 클라 검증은 UX용

## 11. 작업/PR 규칙

- **한 PR에 목적 하나** (구조 변경 + 기능 + 포맷팅 섞지 않기)
- 올리기 전 **`pnpm build` 통과** 확인
- 임시 파일(`tmp-*.mp4` 등) 커밋 금지
- `main`은 **자동 배포** → 올리기 전 로그인/업로드 스모크 테스트
- 공유 브랜치 `git push --force` / 히스토리 재작성 금지

## 12. 추가 전 체크리스트

- [ ] 30초 안에 흐름이 이해되는가? (page는 로딩+조립만?)
- [ ] 계산 가능한 값을 상태로 저장하지 않았는가?
- [ ] `useEffect`가 정말 외부 동기화에 필요한가? (cleanup 있는가?)
- [ ] API는 `@/lib/api/client`만 쓰는가? 공개 엔드포인트는 `auth:false`인가?
- [ ] real 모드에서 가짜 결과를 만들지 않는가? (없으면 상태 UI)
- [ ] loading/empty/error/권한없음 처리했는가?
- [ ] 밝은 색에 `dark:` 변형을 넣었는가? 디자인 토큰을 썼는가?
- [ ] 토큰/비밀값을 storage·로그에 노출하지 않는가?
- [ ] `pnpm build` 통과하는가?
