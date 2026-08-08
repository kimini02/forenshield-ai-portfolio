# 01 Utils Wiring Report

## 1. 단계 / 대상 문서

- 단계: 01 - 공통 유틸 배선
- 대상 문서: `docs/frontend/refactor/01-utils-wiring.md`
- 목적: 이미 생성된 `lib/features.ts`, `lib/formatters.ts`, `lib/status-labels.ts`, `lib/api/errors.ts`를 실제 화면 코드에 점진 배선한다.

## 2. 바꾼 파일 목록

- `lib/formatters.ts`
  - 기존 기본 출력은 유지하면서 `formatDateTime`이 `number | Date`도 받을 수 있게 확장했다.
  - `formatFileSize`에 `zeroLabel`, `minUnit`, `maxUnit`, `trimTrailingZero` 옵션을 추가해 화면별 기존 표시값을 보존하면서 공통 유틸을 재사용할 수 있게 했다.

- `components/login-form.tsx`
  - 로그인 실패 메시지 분기를 `getApiErrorMessage`로 교체했다.

- `app/mypage/_components/mypage-content.tsx`
  - 분석 이력 조회 401 판별을 `isUnauthorizedError`로 교체했다.

- `app/signup/page.tsx`
  - 아이디 중복 확인 실패와 가입 실패 fallback 메시지를 `getApiErrorMessage`로 교체했다.
  - `ApiError.details` 기반 필드 메시지는 기존 우선순위를 유지했다.

- `components/upload-panel.tsx`
  - 업로드/분석/중단 실패 메시지를 `getApiErrorMessage`로 교체했다.
  - 파일 크기 표시를 공통 `formatFileSize`에 위임하되 기존 `0 B`, 소수점 제거 표시를 유지했다.

- `app/main/_components/analysis-request-flow.tsx`
  - 분석 요청 실패 메시지를 `getApiErrorMessage`로 교체했다.
  - 파일 크기와 업로드 시간 라벨을 `formatFileSize`, `formatDateTime`에 위임했다.

- `app/compare/_components/compare-verification-flow.tsx`
  - 비교 검증/목록/상세/다운로드 실패 메시지를 `getApiErrorMessage`로 교체했다.
  - 상태 라벨 일부를 `getAnalysisStatusLabel`로 교체하고, `PENDING`의 축약 라벨 `대기`는 유지했다.
  - 파일 크기 표시를 공통 `formatFileSize`에 위임하되 기존 MB/GB 표시 정책을 유지했다.

- `app/cases/[id]/page.tsx`
  - 사건 상세 fallback 에러 메시지를 `getApiErrorMessage`, 401 판별을 `isUnauthorizedError`로 교체했다.
  - 날짜/파일 크기 표시를 `formatDateTime`, `formatFileSize`로 교체했다.
  - 위험도 기준/라벨 일부를 `getRiskTone`, `getRiskLabel`에 위임했다.
  - 상태 라벨 일부를 `getAnalysisStatusLabel`에 위임했다.

- `app/evidences/[id]/page.tsx`
  - 증거 상세 fallback 에러 메시지를 `getApiErrorMessage`, 401 판별을 `isUnauthorizedError`로 교체했다.
  - 404 특수 안내 문구는 유지했다.

- `app/mypage/edit/page.tsx`
  - 프로필 조회 401 판별을 `isUnauthorizedError`로 교체했다.
  - 저장 실패 토스트의 fallback 메시지를 `getApiErrorMessage`로 교체했다.
  - `ApiError.errorCode` 기반 필드 에러 처리는 유지했다.

- `app/mypage/_components/case-status-badge.tsx`
  - `PENDING`, `COMPLETED`, `FAILED` 라벨을 `getAnalysisStatusLabel`로 연결했다.
  - `PROCESSING`은 화면 기존 문구 `처리 중`을 유지했다.

- `components/metadata-info.tsx`
  - 파일 크기 표시를 공통 `formatFileSize`에 위임하되 기존 `0 B`, 소수점 제거 표시를 유지했다.

- `app/admin/page.tsx`
  - 관리자 대시보드 조회 실패 메시지를 `getApiErrorMessage`로 교체했다.

- `app/admin/statistics/page.tsx`
  - 통계 조회 실패 메시지를 `getApiErrorMessage`로 교체했다.

- `app/admin/evidences/page.tsx`
  - 증거 목록 조회 실패 메시지를 `getApiErrorMessage`로 교체했다.
  - 파일 크기 표시를 공통 `formatFileSize`에 위임하되 기존 최대 MB 정책을 유지했다.

- `app/admin/evidences/[id]/page.tsx`
  - 증거 상세 조회/삭제 실패 메시지를 `getApiErrorMessage`로 교체했다.
  - 파일 크기 표시를 공통 `formatFileSize`에 위임하되 기존 최대 MB 정책을 유지했다.

- `app/admin/logs/page.tsx`
  - 로그 조회/CSV 내보내기 실패 메시지를 `getApiErrorMessage`로 교체했다.

- `app/admin/profile/page.tsx`
  - 관리자 프로필 조회/저장/비밀번호 변경 실패 메시지를 `getApiErrorMessage`로 교체했다.

- `app/admin/invite-codes/page.tsx`
  - 생성코드 조회/발급 실패 메시지를 `getApiErrorMessage`로 교체했다.

- `app/admin/approvals/page.tsx`
  - 승인 목록 조회/처리 실패 메시지를 `getApiErrorMessage`로 교체했다.

- `app/admin/users/page.tsx`
  - 사용자 목록 조회/수정/삭제 실패 메시지를 `getApiErrorMessage`로 교체했다.

## 3. 완료 기준 체크

- OK - 중복 에러처리: 단순 `ApiError ? message : fallback` 패턴은 대부분 `getApiErrorMessage`로 교체했다.
- OK - 401 판별: 단순 401 판별은 `isUnauthorizedError`로 교체했다.
- OK - 날짜/파일 크기 포맷: 출력이 동일하게 유지되는 파일 크기/날짜 포맷은 `lib/formatters.ts`로 배선했다.
- PARTIAL - 상태/위험 라벨: 기준이 동일한 위험도/상태 라벨은 일부 배선했다. 다만 `PROCESSING`의 `처리 중` vs `분석 중`, `PENDING`의 `대기` vs `분석 대기`처럼 화면 문구가 다른 곳은 UI 값 보존을 위해 특수 분기를 유지했다.
- PARTIAL - 화면 내 중복 포맷터 제거: 중복 계산 로직은 공통 유틸에 위임했다. 다만 기존 call site 이름과 표시 정책 보존을 위해 얇은 wrapper 함수는 일부 남아 있다.
- OK - `process.env.NEXT_PUBLIC_*`: 앱/컴포넌트 직접 참조는 없다. 남은 직접 참조는 `lib/features.ts`와 API base URL을 만드는 `lib/api/config.ts`뿐이다.
- OK - `pnpm build`: 통과.
- OK - 새로 생긴 tsc 에러: 없음. 남은 에러는 기존 toast 계열과 `components/upload-panel.tsx` flatMap 타입 에러다. 이번 import 추가로 flatMap 위치가 99에서 100으로 한 줄 밀렸다.

## 4. 검증 결과

### `pnpm build`

- 결과: 통과
- 마지막 실행 요약:
  - `Compiled successfully`
  - `Generating static pages ... (18/18)`
  - Next config상 type validation은 skip 상태다.

### `pnpm exec tsc --noEmit --pretty false`

- 결과: 실패
- 새로 생긴 에러 여부: 없음으로 판단.
- 남은 기존 에러:
  - `app/mypage/edit/page.tsx`: toast 호출 타입 에러.
  - `components/ui/toast.tsx`, `components/ui/use-toast.ts`: toast primitive/type 관련 에러.
  - `components/upload-panel.tsx(100,29)`: 기존 flatMap 타입 에러. import 추가로 기존 안내의 99번 줄에서 100번 줄로 이동했다.

## 5. 회귀 위험 점검

- 이중 직렬화: API 요청 body는 건드리지 않았다.
- 공개 엔드포인트 auth: 로그인/회원가입 API 호출부는 API 함수 호출만 유지했고 auth 옵션은 건드리지 않았다.
- 포맷 출력 동일성: `formatFileSize` 옵션을 추가해 `0 B`, 최대 MB, MB/GB 고정, 소수점 제거 등 기존 표시를 유지했다.
- 위험도 기준: `app/cases/[id]/page.tsx`는 공통 70/40 기준에 위임했고 failed 상태 특수 처리만 유지했다.
- 상태 라벨: 공통 라벨과 화면 문구가 다른 경우는 무리하게 통일하지 않고 특수 분기를 유지했다.
- 표준 ApiError: 모든 helper는 `@/lib/api/client`의 `ApiError` 기준이다.
- `use client`: 변경 파일의 client boundary는 변경하지 않았다.
- 대규모 UI 변경: 없음.

## 6. 판단이 갈렸던 지점

- `lib/status-labels.ts`의 `PROCESSING`은 `분석 중`인데, 일부 화면은 기존에 `처리 중`을 보여준다. 01 문서의 "UI/값 변화 없음" 기준을 우선해 해당 문구는 유지했다.
- `formatDateTime`과 달리 일부 화면은 `toLocaleDateString`, `MM.DD`, `YYYY-MM-DD HH:mm:ss` 같은 별도 포맷을 쓴다. 이들은 공통 유틸로 바꾸면 화면 값이 달라져 보류했다.
- `ApiError` import가 일부 남아 있다. `details`, `errorCode`, 404 특수 문구처럼 표준 helper만으로 표현하면 정보가 줄어드는 곳이라 유지했다.
- `process.env.NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_API_BASE_URL`은 `lib/api/config.ts`의 API base URL 구성 책임이라 `features`로 옮기지 않았다. feature flag는 `lib/features.ts`로 유지한다.

## 7. 분리/이동한 파일 트리

- 01 단계에서 파일 분리/이동 없음.
- 새로 작성한 리포트:

```text
docs/frontend/refactor/reports/
└── 01-utils-wiring.report.md
```

## 8. `git status --short`

```text
M  .gitignore
 M app/admin/approvals/page.tsx
 M app/admin/evidences/[id]/page.tsx
 M app/admin/evidences/page.tsx
 M app/admin/invite-codes/page.tsx
 M app/admin/logs/page.tsx
 M app/admin/page.tsx
 M app/admin/profile/page.tsx
 M app/admin/statistics/page.tsx
 M app/admin/users/page.tsx
MM app/cases/[id]/page.tsx
 M app/compare/_components/compare-verification-flow.tsx
MM app/evidences/[id]/page.tsx
 M app/main/_components/analysis-request-flow.tsx
 M app/main/_components/dashboard-overview.tsx
 M app/mypage/_components/case-history-list.tsx
 M app/mypage/_components/case-status-badge.tsx
 M app/mypage/_components/mypage-content.tsx
 M app/mypage/edit/page.tsx
 M app/signup/page.tsx
 M components/login-form.tsx
 M components/metadata-info.tsx
M  components/site-header.tsx
 M components/upload-panel.tsx
 D lib/api-client.ts
 D lib/api-config.ts
 D lib/api.ts
 M lib/api/admin.ts
 M lib/api/client.ts
 M lib/api/evidence-detail.ts
 M lib/api/mypage.ts
 D lib/api/signup.ts
 M lib/api/user.ts
 M lib/auth-api.ts
 M lib/evidence-api.ts
M  lib/mock-forensic-api.ts
 M lib/signup-api.ts
 M next-env.d.ts
 M tsconfig.tsbuildinfo
?? docs/frontend/
?? lib/api/compare.ts
?? lib/api/errors.ts
?? lib/features.ts
?? lib/formatters.ts
?? lib/status-labels.ts
?? tmp-upload-test.mp4
```
