# Runtime API Error 진단 리포트

## 판정

BACKEND_OR_JWT_DEPENDENCY

## 증상

- 분석 이력 화면에서 "분석 기록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."가 표시된다.
- 비교 검증 화면에서 "서버 오류가 발생했습니다."가 표시된다.
- 비교 검증의 사건 목록이 0건으로 표시된다.

## 관련 화면

- 분석 이력: `app/mypage/_components/mypage-content.tsx`
- 비교 검증: `app/compare/_components/compare-verification-flow.tsx`

## 공통으로 실패하는 API

- `fetchMyAnalysisHistory`
- 분석 이력 화면과 비교 검증 화면이 같은 API 함수를 공유한다.
- 비교 검증은 원본 사건 목록을 만들기 위해 먼저 `fetchMyAnalysisHistory({ sort: "newest", page: 0, size: 50 })`를 호출한다.

## 확인한 파일

- `lib/api/client.ts`
- `lib/api/mypage.ts`
- `lib/api/compare.ts`
- `lib/api/evidence-detail.ts`
- `app/mypage/page.tsx`
- `app/mypage/_components/mypage-content.tsx`
- `app/compare/_components/compare-verification-flow.tsx`
- `app/compare/_components/source-evidence-selector.tsx`
- `components/login-form.tsx`
- `lib/auth.ts`
- `lib/auth-api.ts`
- `lib/api/interceptor.ts`
- `lib/api/errors.ts`
- `lib/api/config.ts`
- `lib/features.ts`
- `next.config.mjs`
- `package.json`

## API 호출 경로

- 함수명: `fetchMyAnalysisHistory`
- endpoint: `/api/v1/mypage/analysis-history?sort={sort}&page={page}&size={size}`
- method: `GET`
- auth 필요 여부: 필요. `apiRequest` 기본값 `auth: true`.
- 사용 API client: `apiRequest` from `lib/api/client.ts`
- credentials: `API_FETCH_CREDENTIALS = "include"`
- Authorization: 메모리 access token이 있으면 `Authorization: Bearer {token}` 추가

비교 검증 관련 추가 경로:

- `fetchCaseDetail(caseId)` -> `/api/v1/cases?caseKey={caseKey}`
- `verifyCompare(evidenceId, file, requestId)` -> `/api/v1/compare/verify?evidenceId={id}&requestId={requestId}`
- 현재 화면 증상은 `verifyCompare` 이전, 원본 사건 목록 로딩 단계에서 발생한다.

## 인증/JWT 상태 판단

- 현재 인증 세션은 `sessionStorage`에 저장하지 않는다.
- access token은 `lib/auth.ts`의 `memorySession`에만 저장된다.
- 새로고침 후에는 HttpOnly refresh cookie로 `/api/auth/refresh`를 호출해 세션 복구를 시도한다.
- `apiRequest`는 access token이 없으면 즉시 `ApiError("로그인이 필요합니다.", 401, "UNAUTHORIZED")`를 던진다.
- 백엔드가 401을 반환하면 `shouldRetryAfterUnauthorized`가 refresh 재시도를 수행하고, 실패하면 로그인 페이지로 이동한다.
- 분석 이력 화면은 401이면 "분석 기록을 보려면 로그인이 필요합니다."를 표시한다.
- 현재 스크린샷의 비교 검증 메시지 "서버 오류가 발생했습니다."는 프론트 fallback 문구가 아니라 백엔드 `ApiError.message`를 표시한 것으로 판단된다.

## mock/real 정책 영향 여부

- `fetchMyAnalysisHistory`는 mock 분기를 사용하지 않고 항상 real API를 호출한다.
- 비교 검증 원본 목록도 `fetchMyAnalysisHistory`와 `fetchCaseDetail` real API 기반이다.
- real API 실패를 mock 데이터로 대체하는 코드는 확인되지 않았다.
- `features.mockApi`는 `evidence-detail` mock 함수 및 admin mock 여부에만 영향을 준다.
- 이번 증상은 mock isolation 또는 fake fallback 제거 때문에 숨겨져 있던 백엔드 오류가 드러난 것에 가깝다.

## 원인 후보

1. 백엔드 `/api/v1/mypage/analysis-history`가 특정 배포 계정/DB 데이터에서 500을 반환한다.
   - 비교 검증은 같은 API를 원본 사건 목록으로 사용하므로 같이 실패한다.
   - 화면의 "서버 오류가 발생했습니다."는 백엔드 표준 500 응답 message와 일치한다.

2. 배포 DB 데이터와 백엔드 DTO/enum/조회 로직 불일치 가능성.
   - 로컬 기준으로는 `MyPageService`가 분석 요청이 있는 evidence만 분석 이력에 포함한다.
   - 업로드 evidence는 있으나 analysis request가 없으면 분석 이력/비교 후보에서 빠질 수 있다.
   - 특정 계정의 `analysis_requests.status`나 연관 evidence 데이터가 백엔드 enum/필수값과 맞지 않으면 500이 날 수 있다.

3. JWT/refresh 세션 문제 가능성.
   - access token은 메모리 저장이므로 새로고침 후 refresh cookie가 정상 전달되어야 한다.
   - 다만 스크린샷의 사용자명이 표시되고 비교 화면에서 401 문구가 아니라 500 message가 표시되므로, 단순 미로그인보다는 백엔드 내부 오류 가능성이 더 높다.

4. 프론트 리팩토링 회귀 가능성은 낮음.
   - 분석 이력/비교 검증 모두 endpoint가 기존 계약과 맞다.
   - `apiRequest`는 `credentials: include`와 Bearer token을 모두 사용한다.
   - 03-C 분리 후에도 state/effect/API 호출 위치는 원 파일에 남아 있다.
   - `fetchMyAnalysisHistory` 사용, endpoint, method, auth 기본값에서 명확한 프론트 회귀는 확인되지 않았다.

## 실제 수정한 내용

수정 없음.

## 수정하지 않은 이유

- 명확한 프론트 회귀가 확인되지 않았다.
- 에러를 숨기기 위해 catch에서 빈 배열을 반환하거나 mock fallback을 넣는 것은 금지 조건이며, 실제 장애를 가린다.
- 비교 검증의 1단계는 분석 이력 API에 의존하므로 백엔드/JWT/DB 실제 status code와 서버 로그 확인이 선행되어야 한다.

## 검증 명령 결과

| 명령 | exit code | 결과 |
| -- | --------: | -- |
| `git status --short` | 0 | `next-env.d.ts`, `tsconfig.tsbuildinfo` 수정 및 문서/임시 mp4 미추적 파일 확인 |
| `git diff --name-status` | 0 | `next-env.d.ts`, `tsconfig.tsbuildinfo`만 tracked diff |
| `git diff --stat` | 0 | 2 files changed, 2 insertions, 2 deletions |
| `git diff --check` | 0 | whitespace error 없음 |
| `pnpm build` | 0 | Next build 성공. Type validation은 설정상 skip |
| `pnpm lint` | 127 | `eslint: command not found` |
| `pnpm typecheck` | 1 | `typecheck` script 없음 |
| `pnpm test` | 1 | `test` script 없음 |

## 남은 확인 필요 사항

- 백엔드 서버 상태
  - 배포 health endpoint는 정상 응답 가능성이 확인되었으나, 실제 로그인 사용자 토큰으로 `/api/v1/mypage/analysis-history` 호출 시 서버 로그 확인 필요.
- JWT 연결 상태
  - 배포 브라우저에서 refresh cookie가 전달되는지 Network의 `/api/auth/refresh`와 `/api/v1/mypage/analysis-history` status 확인 필요.
- API endpoint 계약
  - 분석 이력 API가 업로드만 된 evidence를 포함해야 하는지, analysis request가 있는 evidence만 포함해야 하는지 백엔드와 계약 확정 필요.
- 브라우저 Network 실제 status code
  - 분석 이력 화면: `/api/v1/mypage/analysis-history`
  - 비교 검증 화면: `/api/v1/mypage/analysis-history`, 이후 `/api/v1/cases?caseKey=...`
- AWS DB 확인
  - 배포 백엔드는 prod profile의 PostgreSQL/RDS를 사용한다.
  - 실제 DB 내용은 AWS RDS 콘솔/Query Editor 또는 kubectl secret의 RDS endpoint와 DB 계정으로 접속해 확인 가능하다.
  - 현재 로컬 kube context는 인증 만료로 `db-credentials` secret 값을 직접 조회하지 못했다.

## 다음 액션

- 프론트에서 해야 할 것
  - 브라우저 Network에서 `/api/v1/mypage/analysis-history`의 실제 status code와 response body를 확인한다.
  - 500이면 프론트 수정 없이 백엔드 로그로 넘긴다.
  - 401/403이면 JWT refresh cookie/Authorization header 전달 여부를 확인한다.
  - 404이면 배포 API base URL 또는 ingress rewrite 문제를 확인한다.

- 백엔드/JWT 담당자가 확인해야 할 것
  - 배포 pod 로그에서 `/api/v1/mypage/analysis-history` exception stack trace 확인.
  - RDS `evidences`, `analysis_requests`, `analysis_results` 데이터 중 현재 로그인 사용자 데이터 확인.
  - `analysis_requests.status`가 enum과 일치하는지, null/이상값이 없는지 확인.
  - 업로드만 된 evidence를 분석 이력/비교 원본 후보에 포함해야 하는지 정책 확정.

- 03-D 리팩토링을 재개해도 되는지 여부
  - 지금은 재개하지 않는 것이 안전하다.
  - 런타임 오류의 실제 Network status와 백엔드 로그 확인 후 재개 권장.
