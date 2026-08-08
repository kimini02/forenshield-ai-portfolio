# REST API 설계 규칙 + 에러 코드

> **기준:** `기능명세서_최종.xlsx` · RQ-NFR-160~162  
> **구현:** Spring Boot 3 · JSON · JWT  
> **상세 API 목록:** [specification.md](./specification.md)

---

## 1. Base URL

| 구분 | Base | 비고 |
| :--- | :--- | :--- |
| **표준 (v1)** | `/api/v1` | 신규·관리자·마이페이지 API |
| **Legacy** | `/api/auth/login` | 로그인만 예외 (증거 API는 v1 only) |

**개발 환경 예:** `http://localhost:8080`

### 1.1 경로 마이그레이션 (현재 → 목표)

| 현재 (코드) | 명세 목표 | 상태 |
| :--- | :--- | :--- |
| `POST /api/auth/login` | 동일 (legacy 유지) | ✅ |
| `POST /api/v1/auth/signup` | 동일 | ✅ |
| `POST /api/evidences/upload` | `POST /api/v1/evidences/upload` | ✅ v1 only (legacy removed) |
| `POST /api/evidences/analyze` | `POST /api/v1/evidences/analyze` | ✅ v1 only |
| `GET /api/evidences/{id}/detail` | `GET /api/v1/evidences/{evidenceId}/detail` | ✅ v1 only |

**규칙:** 신규 API는 **반드시 `/api/v1`** 로만 추가합니다.

---

## 2. HTTP Method · URL · 인증

Method: GET(조회) · POST(생성/액션) · PATCH(부분수정) · DELETE(삭제) · PUT(MVP 거의 미사용)

URL 패턴: `/api/v1/{resource}`, `/api/v1/admin/{resource}` — 복수형·kebab-case

JWT: `Authorization: Bearer {token}` · Admin은 `ROLE_ADMIN` + `/api/v1/admin/**`

로그인 성공 응답·sessionStorage 저장: [specification.md §2.1](./specification.md)

---

## 3. Request / Response

- **camelCase**, 날짜 ISO 8601, Enum 대문자
- 성공: DTO 직접 반환 (204는 본문 없음)
- 페이지: `content`, `page`, `size`, `totalElements`, `totalPages`

### 3.1 에러 JSON (표준)

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "details": [{ "field": "loginId", "reason": "필수 항목입니다." }]
}
```

응답·예외 처리 상세: [../guides/implementation-standards.md](../guides/implementation-standards.md)

---

## 4. HTTP Status Code

| Code | 사용 |
| :--- | :--- |
| 200 / 201 / 204 | 성공 |
| 400 | Validation·도메인 규칙 |
| 401 / 403 | 인증·권한 |
| 413 | Payload·multipart 한도 초과 |
| 404 / 409 / 429 / 500 | Not found · 중복 · Rate limit · 서버 오류 |

---

## 5. errorCode 목록

| HTTP | errorCode | 사용처 |
| :--- | :--- | :--- |
| 400 | `VALIDATION_ERROR` | `@Valid` 실패 |
| 400 | `INVALID_REQUEST`, `FILE_NOT_FOUND`, `UNSUPPORTED_FILE_TYPE`, `FILE_SIZE_EXCEEDED`, `INVALID_MEDIA_FILE` | upload·미디어 검증 (`FILE_SIZE_EXCEEDED` = 비즈니스 한도) |
| 400 | `INVALID_PASSWORD`, `PASSWORD_TOO_SHORT` | 프로필 |
| 400 | `ANALYSIS_ALREADY_COMPLETED` | analyze |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS` | JWT·로그인 (`SecurityFilter` → `StandardErrorResponse`) |
| 401 | `ACCOUNT_PENDING`, `ACCOUNT_REJECTED`, `ACCOUNT_SUSPENDED` | 계정 상태 |
| 403 | `FORBIDDEN` | 권한 없음 (Admin·역할 불일치) |
| 413 | `FILE_TOO_LARGE` | Servlet multipart 한도 초과 (`MaxUploadSizeExceededException`) |
| 404 | `NOT_FOUND`, `EVIDENCE_NOT_FOUND`, `CASE_NOT_FOUND` | 리소스 |
| 409 | `DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL` | signup |
| 409 | `SIGNATURE_INVALID`, `CHAIN_INTEGRITY_FAILED`, `BLOCKCHAIN_HASH_MISMATCH` | `GET .../integrity/verify` (SK-632) |
| 429 | `RATE_LIMIT_EXCEEDED` | Public API |
| 500 | `INTERNAL_ERROR`, `HASH_GENERATION_FAILED` | 서버 |

**파일 크기 (업로드):** FE는 **413 `FILE_TOO_LARGE`**(Servlet 한도)와 **400 `FILE_SIZE_EXCEEDED`**(비즈니스 검증)를 **둘 다** 처리합니다.

**신규 errorCode:** 본 표 + [specification.md](./specification.md) 해당 API 동시 갱신

---

## 6. Rate Limit (Public)

| API | 제한 |
| :--- | :--- |
| `POST /api/v1/auth/signup` | IP당 1분 5회 |
| `GET /api/v1/auth/username/check` | IP당 1분 20회 |
| `POST /api/v1/invite-codes/validate` | IP당 1분 10회 |

상세: [signup.md §4.3](./signup.md)

---

## 7. Swagger

로컬 `/swagger-ui.html` · PR 시 `@Tag`, `@Operation` 필수

---

## 8. 관련 문서

- [specification.md](./specification.md) — 엔드포인트·Gap 분석
- [signup.md](./signup.md) — 회원가입
- [../integrations/ai-json.md](../integrations/ai-json.md) — AI 메시지 JSON
