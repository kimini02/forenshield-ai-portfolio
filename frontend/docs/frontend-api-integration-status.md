# Frontend API Integration Status

> 작성일: 2026-06-27  
> 기준 코드: `frontend-forensic` 현재 로컬 작업본  
> 목적: 백엔드 리팩토링 전에 프론트엔드가 실제로 호출하는 API, 환경변수, 플래그, 주의 지점을 정리한다.

## 결론

현재 프론트엔드는 mock 전용 상태가 아니라 실제 백엔드 API와 연동되어 있다.

- API base URL: `http://localhost:8080`
- 표준 클라이언트: `lib/api/client.ts`
- 현재 로컬 설정: `NEXT_PUBLIC_USE_MOCK_API=false`
- 단, `NEXT_PUBLIC_UPLOAD_ONLY_MODE=true`라서 메인 업로드 플로우는 파일 업로드까지만 수행하고 분석 시작 API는 호출하지 않는다.

기존 `docs/API_SPEC.md` 상단에는 "fetch/axios 미연동, mock 사용"이라고 되어 있는데, 현재 코드와 맞지 않는 과거 문서다. 실제 연동 현황은 이 문서를 우선 기준으로 본다.

## 환경변수

현재 `frontend-forensic/.env.local`:

```env
BACKEND_API_ORIGIN=http://localhost:8080
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_USE_MOCK_API=false
NEXT_PUBLIC_UPLOAD_ONLY_MODE=true
```

| 변수 | 현재값 | 의미 |
| --- | --- | --- |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | 브라우저에서 직접 호출하는 백엔드 origin |
| `NEXT_PUBLIC_API_BASE_URL` | 미설정 | `NEXT_PUBLIC_API_URL`이 없을 때 fallback |
| `BACKEND_API_ORIGIN` | `http://localhost:8080` | Next.js rewrite destination |
| `NEXT_PUBLIC_USE_MOCK_API` | `false` | `true`일 때 일부 화면이 mock 데이터를 사용 |
| `NEXT_PUBLIC_UPLOAD_ONLY_MODE` | `true` | 업로드 후 분석 요청을 보내지 않는 임시 모드 |

API base URL 결정 로직:

```ts
// lib/api/config.ts
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "") ??
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ??
  "http://localhost:8080"
```

## 호출 방식

대부분 API는 `lib/api/client.ts`의 세 함수로 호출한다.

| 함수 | 용도 | 특징 |
| --- | --- | --- |
| `apiRequest<T>()` | JSON 요청/응답 | `Content-Type: application/json`, Bearer token 자동 첨부 |
| `apiRequestForm<T>()` | `multipart/form-data` 업로드 | `Content-Type` 직접 설정하지 않음 |
| `apiDownload()` | PDF/CSV 등 Blob 다운로드 | Bearer token 첨부 |

공통 특징:

- 실제 요청 URL은 `${API_BASE_URL}${path}` 형태다.
- 인증이 필요한 요청은 메모리 세션의 access token을 `Authorization: Bearer {token}`으로 보낸다.
- 모든 요청은 `credentials: "include"`를 사용한다.
- 401 응답을 받으면 refresh API를 한 번 시도하고, 성공하면 원래 요청을 재시도한다.
- access token은 브라우저 storage에 저장하지 않고 메모리에만 둔다.
- refresh token은 백엔드의 HttpOnly cookie를 기대한다.

직접 `fetch`를 쓰는 예외:

| 파일 | API | 이유 |
| --- | --- | --- |
| `lib/auth.ts` | `POST /api/auth/refresh` | 401 재시도 및 새로고침 세션 복구 |
| `lib/auth-api.ts` | `POST /api/auth/logout` | 토큰이 없을 수도 있어 별도 처리 |

## Next.js Rewrite와 실제 호출 경로

`next.config.mjs`에는 다음 rewrite가 있다.

| Frontend path | Destination |
| --- | --- |
| `/api/v1/:path*` | `${BACKEND_API_ORIGIN}/api/v1/:path*` |
| `/api/evidences/:path*` | `${BACKEND_API_ORIGIN}/api/evidences/:path*` |

하지만 현재 표준 API 클라이언트는 `http://localhost:8080/api/...`처럼 절대 URL로 직접 요청한다. 따라서 일반 화면 호출은 Next.js rewrite를 거치지 않고 백엔드 8080을 직접 친다고 보면 된다.

## 현재 프론트가 사용하는 API

### Auth

Source: `lib/auth-api.ts`, `lib/auth.ts`

| Method | Path | Auth | 사용처/비고 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | Public | 로그인 |
| `POST` | `/api/auth/refresh` | Cookie | 세션 복구, 401 재시도 |
| `POST` | `/api/auth/logout` | Optional Bearer + Cookie | 로그아웃 |

로그인 응답에서 프론트가 기대하는 필드:

```ts
{
  success: boolean
  token: string
  accessToken?: string
  accessTokenExpiresIn?: number
  userId: number
  loginId: string
  name: string
  role: "ROLE_USER" | "ROLE_ADMIN"
}
```

### Signup

Source: `lib/signup-api.ts`

| Method | Path | Auth | Query/Body |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/signup` | Public | `SignupRequest` JSON |
| `GET` | `/api/v1/auth/username/check` | Public | `loginId` query |
| `POST` | `/api/v1/invite-codes/validate` | Public | `{ code }` |
| `GET` | `/api/v1/organizations/departments` | Public | `organizationType` query |

### User / My Page

Source: `lib/api/user.ts`, `lib/api/mypage.ts`

| Method | Path | Auth | Query/Body |
| --- | --- | --- | --- |
| `GET` | `/api/v1/users/me` | Required | - |
| `PATCH` | `/api/v1/users/me` | Required | `UpdateUserProfilePayload` |
| `GET` | `/api/v1/mypage/analysis-history` | Required | `sort`, `page`, `size` |

`/api/v1/mypage/analysis-history`는 `features.mockApi=true`일 때만 mock으로 대체된다. 현재 로컬은 `false`라 실제 API를 호출한다.

### Dashboard / Evidence / Analysis

Source: `lib/evidence-api.ts`

| Method | Path | Auth | 현재 호출 여부 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/evidences/stats` | Required | 대시보드에서 호출 |
| `GET` | `/api/v1/evidences/stats/trend?days={days}` | Required | 대시보드에서 호출 |
| `GET` | `/api/v1/evidences/stats/recent?limit={limit}` | Required | 대시보드에서 호출 |
| `POST` | `/api/v1/evidences/upload` | Required | 메인 업로드에서 호출 |
| `POST` | `/api/v1/evidences/analyze` | Required | 코드상 존재, 현재 upload-only 모드에서는 스킵 |
| `GET` | `/api/v1/evidences/{evidenceId}/analysis-status` | Required | 분석 상태 polling용 |
| `DELETE` | `/api/v1/evidences/{evidenceId}` | Required | 업로드/증거 취소 |
| `DELETE` | `/api/v1/evidences/{evidenceId}/reset` | Required | 증거 초기화 |
| `DELETE` | `/api/v1/evidences/{evidenceId}/analysis` | Required | 분석 취소 |

업로드 요청:

- `multipart/form-data`
- field: `file`
- optional field: `caseName`

프론트가 업로드 응답에서 사용하는 필드:

```ts
{
  success: boolean
  message: string
  evidenceId: number
  fileName: string
  caseName?: string | null
  fileSize: number
  hashAlgorithm: string
  hashValue: string
  metadata: object | string | null
}
```

현재 `NEXT_PUBLIC_UPLOAD_ONLY_MODE=true`일 때 메인 플로우:

1. `POST /api/v1/evidences/upload` 호출
2. 업로드 성공 후 첫 번째 `evidenceId`로 케이스 상세 화면 이동
3. `POST /api/v1/evidences/analyze`는 호출하지 않음

전체 분석 플로우를 테스트하려면 `NEXT_PUBLIC_UPLOAD_ONLY_MODE=false`로 바꿔야 한다.

### Evidence Detail / Case Detail

Source: `lib/api/evidence-detail.ts`

| Method | Path | Auth | Query/Body |
| --- | --- | --- | --- |
| `GET` | `/api/v1/evidences/{evidenceId}/detail` | Required | - |
| `GET` | `/api/v1/cases` | Required | `caseKey` query |

둘 다 `features.mockApi=true`일 때만 mock으로 대체된다. 현재 로컬은 `false`라 실제 API를 호출한다.

케이스 상세 진입 시 `caseName` route param을 decode해서 `caseKey` query로 보낸다.

### Compare

Source: `lib/api/compare.ts`

| Method | Path | Auth | Query/Body |
| --- | --- | --- | --- |
| `POST` | `/api/v1/compare/verify` | Required | `evidenceId`, optional `requestId`, `file` FormData |
| `POST` | `/api/v1/compare/cancel` | Required | `requestId` query |
| `GET` | `/api/v1/compare/{compareId}` | Required | - |
| `GET` | `/api/v1/compare/{compareId}/reports/pdf` | Required | Blob download |

비교 검증은 AI 분석 플로우와 별개로 백엔드의 비교 API를 호출한다.

### Admin

Source: `lib/api/admin.ts`

| Method | Path | Auth | Query/Body |
| --- | --- | --- | --- |
| `GET` | `/api/v1/admin/dashboard/stats` | Admin | - |
| `GET` | `/api/v1/admin/dashboard/analysis-stats` | Admin | - |
| `GET` | `/api/v1/admin/users` | Admin | `search`, `status`, `page`, `size` |
| `POST` | `/api/v1/admin/users/{userId}/approve` | Admin | - |
| `POST` | `/api/v1/admin/users/{userId}/reject` | Admin | - |
| `PATCH` | `/api/v1/admin/users/{userId}` | Admin | `UpdateAdminUserPayload` |
| `PATCH` | `/api/v1/admin/users/{userId}/password` | Admin | `{ newPassword }` |
| `DELETE` | `/api/v1/admin/users/{userId}` | Admin | - |
| `GET` | `/api/v1/admin/invite-codes` | Admin | - |
| `POST` | `/api/v1/admin/invite-codes` | Admin | `{ expiresInDays }` |
| `GET` | `/api/v1/admin/logs` | Admin | `category`, `department`, `search`, `page`, `size` |
| `GET` | `/api/v1/admin/logs/export` | Admin | `format=csv`, optional filters |
| `GET` | `/api/v1/admin/me` | Admin | - |
| `PATCH` | `/api/v1/admin/me` | Admin | `UpdateAdminProfilePayload` |
| `PATCH` | `/api/v1/admin/me/password` | Admin | `{ currentPassword, newPassword }` |
| `GET` | `/api/v1/admin/evidences` | Admin | `search`, `fileType`, `status`, `page`, `size` |
| `GET` | `/api/v1/admin/evidences/{evidenceId}` | Admin | - |
| `DELETE` | `/api/v1/admin/evidences/{evidenceId}` | Admin | `{ reason }` |

Admin API는 `withMockFallback()` 래퍼가 있지만, 현재 `NEXT_PUBLIC_USE_MOCK_API=false`라서 백엔드 오류가 나도 mock으로 fallback하지 않는다. 즉 현재는 실제 백엔드 응답/에러가 그대로 화면에 반영된다.

`fetchAdminUsageTrend()`는 별도 백엔드 trend API를 직접 호출하지 않고, 내부적으로 `fetchAdminLogs()`를 여러 번 호출해서 프론트에서 집계한다.

## Mock 사용 지점

`NEXT_PUBLIC_USE_MOCK_API=true`일 때만 mock이 켜지는 곳:

| 파일 | Mock 대상 |
| --- | --- |
| `lib/api/evidence-detail.ts` | 증거 상세, 케이스 상세 |
| `lib/api/mypage.ts` | 내 분석 기록 |
| `lib/api/admin.ts` | 관리자 데이터 fallback |

현재 로컬 설정은 `NEXT_PUBLIC_USE_MOCK_API=false`이므로 위 mock들은 사용되지 않는다.

## 백엔드 리팩토링 시 깨지기 쉬운 계약

아래 계약이 바뀌면 프론트 수정이 필요하다.

1. Auth 경로는 현재 `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`을 사용한다. 로그인은 `/api/v1/auth/login`이 아니다.
2. Refresh는 HttpOnly cookie 기반을 기대하며, 응답에 access token과 사용자 정보가 함께 있어야 세션 복구가 된다.
3. 프론트는 access token을 JS 메모리에만 보관하고 매 요청마다 Bearer header로 보낸다.
4. 업로드 응답의 `evidenceId`, `fileName`, `fileSize`, `hashAlgorithm`, `hashValue`, `metadata` 필드는 화면에서 바로 사용한다.
5. 케이스 상세은 `/api/v1/cases?caseKey=...`를 호출한다. `/api/v1/cases/{caseId}`가 아니라 query 기반이다.
6. 마이페이지는 `/api/v1/mypage/analysis-history`를 호출한다. OpenAPI에 `/api/v1/cases/me`도 있지만 현재 프론트는 그 경로를 쓰지 않는다.
7. Admin list API는 Spring Page 스타일의 `content`, `page`, `size`, `totalElements`, `totalPages` 계열 응답을 기대한다.
8. 파일 업로드와 비교 검증은 `multipart/form-data`이며, 프론트가 `Content-Type`을 직접 지정하지 않는다.
9. API 오류 응답은 `message`, `errorCode` 또는 `error`, `details`를 읽어 사용자 메시지로 변환한다.
10. `NEXT_PUBLIC_UPLOAD_ONLY_MODE=true` 상태에서는 분석 시작 API가 안 불리므로, 분석 API 리팩토링 검증 시 이 플래그를 반드시 끄고 테스트해야 한다.

## 프론트 소스 위치

| 영역 | 파일 |
| --- | --- |
| API base URL | `lib/api/config.ts` |
| 공통 API client | `lib/api/client.ts` |
| 401 refresh/retry | `lib/api/interceptor.ts`, `lib/auth.ts` |
| 로그인/로그아웃 | `lib/auth-api.ts` |
| 회원가입 | `lib/signup-api.ts` |
| 업로드/분석/대시보드 | `lib/evidence-api.ts` |
| 증거/케이스 상세 | `lib/api/evidence-detail.ts` |
| 마이페이지 | `lib/api/mypage.ts` |
| 유저 프로필 | `lib/api/user.ts` |
| 비교 검증 | `lib/api/compare.ts` |
| 관리자 | `lib/api/admin.ts` |
| 플래그 | `lib/features.ts` |
| Next rewrite | `next.config.mjs` |

## 빠른 확인 방법

프론트 로컬 실행 후 브라우저 DevTools Network에서 다음을 확인한다.

1. 로그인: `POST http://localhost:8080/api/auth/login`
2. 업로드: `POST http://localhost:8080/api/v1/evidences/upload`
3. 대시보드: `GET http://localhost:8080/api/v1/evidences/stats`
4. 케이스 상세: `GET http://localhost:8080/api/v1/evidences/{id}/detail`, `GET http://localhost:8080/api/v1/cases?caseKey=...`

분석 시작까지 검증하려면 `.env.local`에서 아래처럼 변경 후 프론트 dev server를 재시작한다.

```env
NEXT_PUBLIC_UPLOAD_ONLY_MODE=false
```

