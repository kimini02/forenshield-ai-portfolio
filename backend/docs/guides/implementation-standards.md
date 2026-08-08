# 팀 공통 구현 표준 (API 응답 · 예외 · 협업 계약)

> **대상:** 백엔드 · 프론트엔드 · AI (REST/JSON 경계)  
> **상위 문서:** [../rule.md](../rule.md) · [../api/convention.md](../api/convention.md)

팀원마다 Controller에 try-catch를 두거나, 에러 JSON 필드명이 달라지면 **프론트 분기·테스트·문서가 동시에 깨집니다.**  
본 문서는 **“앞으로 이렇게 맞춘다”**는 팀 계약이며, 현재 코드와 다른 부분은 **§9 마이그레이션**에 정리했습니다.

---

## 1. 한눈에 보는 통일 항목

| 영역 | 통일 기준 | 상세 |
| :--- | :--- | :--- |
| **에러 JSON** | `success` + `errorCode` + `message` (+ `details`) | §2 |
| **HTTP 상태** | 의미별 고정 (401/403/404/409/429) | §3 · [../api/convention.md §5](../api/convention.md) |
| **Validation** | Controller `@Valid` → `GlobalExceptionHandler` | §4 |
| **예외 처리 위치** | Service는 도메인 예외 throw, Controller는 얇게 | §5 |
| **성공 응답** | DTO 직접 반환 (불필요한 wrapper 지양) | §6 |
| **페이지네이션** | `content` + `page` + `size` + `totalElements` + `totalPages` | §7 |
| **인증** | `Authorization: Bearer {JWT}` | §8 |
| **날짜·시간** | ISO 8601 UTC | §9 |
| **로그** | 비밀번호·토큰·초대코드 원문 금지 | §10 |

---

## 2. 에러 응답 JSON (팀 표준)

### 2.1 표준 형식 (신규 API 필수)

모든 4xx/5xx는 **아래 JSON 하나**로 통일합니다.

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "details": [
    { "field": "loginId", "reason": "필수 항목입니다." }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| :--- | :--- | :---: | :--- |
| `success` | boolean | ✅ | 항상 `false` |
| `errorCode` | string | ✅ | `UPPER_SNAKE_CASE` — FE 분기 키 |
| `message` | string | ✅ | 사용자에게 보여줄 한국어 (1~2문장) |
| `details` | array | ❌ | 필드 검증 시만. 없으면 `[]` 또는 생략 |

- **`errorCode` 목록:** [../api/convention.md §5](../api/convention.md)  
- **`message`:** 사용자용. 내부 스택트레이스·SQL 노출 금지  
- **`details[].field`:** Request JSON의 camelCase 필드명  
- **`details[].reason`:** 해당 필드 오류 한 줄

### 2.2 Validation 오류 (400)

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "details": [
    { "field": "email", "reason": "올바른 이메일 형식이 아닙니다." }
  ]
}
```

- `@NotBlank`, `@Email` 등 메시지는 **한국어** (`message = "..."`)
- 가능하면 **모든 필드 오류**를 `details`에 담기 (첫 번째만 X)

### 2.3 Rate Limit (429)

```json
{
  "success": false,
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "message": "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
  "details": []
}
```

- HTTP Header: `Retry-After: {seconds}` (초)

---

## 3. HTTP Status ↔ errorCode 매핑

**HTTP는 프로토콜, `errorCode`는 앱 로직**입니다. 둘 다 맞춰야 합니다.

| HTTP | errorCode (예) | 언제 |
| :---: | :--- | :--- |
| 400 | `VALIDATION_ERROR`, `INVALID_REQUEST`, 도메인별 400 | 입력·비즈니스 규칙 위반 |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS` | JWT 없음·만료·로그인 실패 |
| 401 | `ACCOUNT_PENDING`, `ACCOUNT_REJECTED`, `ACCOUNT_SUSPENDED` | 계정 상태로 로그인 거부 ([convention](../api/convention.md)) |
| 403 | `FORBIDDEN` | 인증됐으나 Admin API 등 권한 없음 |
| 404 | `NOT_FOUND`, `EVIDENCE_NOT_FOUND`, `ANALYSIS_NOT_FOUND` | 리소스 없음 |
| 409 | `DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL` | 중복 |
| 429 | `RATE_LIMIT_EXCEEDED` | Public API 제한 |
| 500 | `INTERNAL_ERROR`, `HASH_GENERATION_FAILED` | 예상 못 한 서버 오류 |

**금지**

- 200 OK + body에 `success: false`  
- 500인데 `message`에 SQL/경로 노출  
- 같은 상황인데 API마다 다른 HTTP 코드

---

## 4. Validation 규칙

### 4.1 Controller

```java
@PostMapping("/api/v1/auth/signup")
public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(signupService.signup(request));
}
```

- Request DTO에 Jakarta Validation (`@NotBlank`, `@Size`, `@Pattern` …)
- Query param: `@Validated` on Controller + `@NotBlank` on param

### 4.2 Service

- **형식 검증:** DTO `@Valid` (Controller)
- **비즈니스 검증:** Service에서 **도메인 예외 throw** (중복, 초대코드, 분석 상태 등)
- Service에서 `ErrorResponse`를 만들어 반환 **하지 않음**

### 4.3 비밀번호 확인 등 FE 전용 필드

- `passwordConfirm` → API/DB **저장·검증 안 함** (FE only)  
- 서버는 `password` 규칙만 검증 ([../api/signup.md](../api/signup.md))

---

## 5. 예외 처리 패턴 (백엔드)

### 5.1 권장 구조

```
Controller  →  Service  →  Repository
     ↑              ↑
     │         DomainException throw
     └──── GlobalExceptionHandler → 표준 JSON
```

| 레이어 | 역할 |
| :--- | :--- |
| **Controller** | `@Valid`, `ResponseEntity` status, 성공 DTO 반환 |
| **Service** | `@Transactional`, 비즈니스 규칙, `XxxException` throw |
| **GlobalExceptionHandler** | 예외 → HTTP + 표준 에러 JSON |

### 5.2 도메인 예외 클래스 (패턴)

```java
@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
```

**현재 코드 (2026-06-17 통일 완료)**

| 클래스 | 용도 | Handler |
| :--- | :--- | :--- |
| `BusinessException` | 공통 베이스 (status + errorCode + message + details) | `GlobalExceptionHandler` |
| `AuthException` | 로그인·인증 | ↑ (상속) |
| `AdminException` | 관리자 | ↑ (상속) |
| `DuplicateSignupFieldException` | 가입 중복 | ↑ (상속) |
| `InvalidInviteCodeException` | 초대코드 | ↑ (상속) |
| `UnsupportedFileTypeException` 등 | 파일 업로드 | ↑ (상속) |

**에러 JSON DTO:** `StandardErrorResponse` (`success`, `errorCode`, `message`, `details`)

### 5.3 Controller try-catch — 언제 쓰지 말 것

**지양 (현재 EvidenceController 등에 산재):**

```java
try {
    return ResponseEntity.ok(service.do());
} catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(ErrorResponse.builder()...);
}
```

**권장:**

```java
// Service
throw new BusinessException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다.");

// GlobalExceptionHandler
@ExceptionHandler(BusinessException.class)
public ResponseEntity<StandardErrorResponse> handle(BusinessException ex) { ... }
```

- 새 API는 **Controller try-catch 금지** (레거시 수정 시 Handler로 이전)

### 5.4 `@RestControllerAdvice`

- **`GlobalExceptionHandler` 단일 진입점** — signup·evidence·admin·auth 모두 동일 JSON

---

## 6. 성공 응답 JSON

### 6.1 원칙

| 유형 | HTTP | Body |
| :--- | :---: | :--- |
| 단일 조회/생성 | 200 / 201 | 리소스 DTO |
| 수정 | 200 | 갱신된 DTO |
| 삭제·비밀번호 변경 | **204** | **본문 없음** |
| 목록(페이지) | 200 | §7 페이지 DTO |

- 성공 시 **`success: true`는 선택** — 명세·FE 합의된 API만 유지 (로그인, 업로드 등)
- 신규 API는 **`success` 없이 DTO만** 반환 권장 (중복 필드 줄이기)

### 6.2 로그인 성공 (예외적으로 `success` + 토큰)

```json
{
  "success": true,
  "token": "<JWT>",
  "userId": 1,
  "loginId": "hong123",
  "name": "홍길동",
  "role": "ROLE_USER"
}
```

### 6.3 생성 성공 (201)

```json
{
  "userId": 10,
  "status": "PENDING",
  "message": "가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다."
}
```

- `Location` 헤더는 선택 (MVP 미사용)

### 6.4 업로드 성공 (200, `success` 포함 — 레거시)

```json
{
  "success": true,
  "message": "파일 업로드 완료",
  "evidenceId": 1,
  "hashValue": "...",
  "metadata": { }
}
```

---

## 7. 페이지네이션 (통일 목표)

**신규·수정 API는 아래 필드명을 사용합니다.**

```json
{
  "content": [ { "...": "..." } ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

| Query | 기본값 | 설명 |
| :--- | :--- | :--- |
| `page` | `0` | 0-based |
| `size` | `10` (관리자 로그 `8`) | 페이지 크기 |
| `sort` | API별 | `newest`, `oldest` 등 |

**현재:** Admin·MyPage 목록 API 모두 `content`, `totalElements`, `totalPages` 사용

---

## 8. 인증 · 권한

### 8.1 요청 헤더

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json
```

### 8.2 Controller에서 사용자

```java
authUserResolver.requireCurrentUser()  // User 엔티티
```

- 직접 `SecurityContext` 파싱 **지양** — `AuthUserResolver` 사용

### 8.3 Admin API

```java
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/...")
```

- 403 + `errorCode: FORBIDDEN` ([RQ-NFR-162](../requirements/index.md))

### 8.4 Public API

- signup, login, username check, invite validate, departments  
- Rate limit 적용 ([../api/signup.md](../api/signup.md) §4.3)

---

## 9. 날짜 · ID · Enum

| 항목 | 규칙 |
| :--- | :--- |
| API 날짜 | `2026-06-17T09:30:00Z` (ISO 8601) |
| API ID | `Long` (`userId`, `evidenceId`) — JSON number |
| Enum | 문자열 대문자 (`APPROVED`, `QUEUED`, `ROLE_USER`) |
| JSON 필드 | **camelCase** ([naming.md](./naming.md)) |

---

## 10. 로깅 · 보안

**로그에 남기지 않음**

```
password, Authorization header, JWT 전체, DB password, 초대코드 전체
```

초대코드 로그 필요 시: `VF-A3K9-****`

**서버 로그**

- ERROR: 예외 stack (사용자 message와 분리)
- INFO: evidenceId, userId, action (민감정보 제외)
- CoC: `CustodyLogs` DB가 정본, 애플리케이션 로그는 보조

---

## 11. 프론트엔드 처리 계약

### 11.1 공통 fetch 래퍼 (권장)

```typescript
type ApiError = {
  success: false;
  errorCode: string;
  message: string;
  details?: { field: string; reason: string }[];
};

async function api<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const res = await fetch(input, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as Partial<ApiError>;
    throw { status: res.status, ...body };
  }

  if (res.status === 204) return undefined as T;
  return res.json();
}
```

### 11.2 분기 우선순위

1. `status === 401` → sessionStorage 클리어 → `/login` (단, `ACCOUNT_PENDING` 등은 메시지 표시)
2. `errorCode === 'RATE_LIMIT_EXCEEDED'` → 재시도 안내
3. `details?.length` → 필드 아래 inline error
4. 그 외 → `message` 토스트

### 11.3 레거시 호환 (과도기)

| 레거시 필드 | 표준 | 상태 |
| :--- | :--- | :---: |
| `error` (signup) | `errorCode` | ✅ 제거 |
| `items` / `total` | `content` / `totalElements` | ✅ Admin API 반영 완료 |

FE는 과도기 호환 불필요(Admin API는 동시 배포). 신규 코드는 `content` / `totalElements`만 사용.

---

## 12. AI · 비동기 (REST 아님)

REST 에러 형식과 별도로 RabbitMQ JSON은 [../integrations/ai-json.md](../integrations/ai-json.md)를 따릅니다.

| AI status | BE 처리 |
| :--- | :--- |
| `COMPLETED` | `AnalysisRequests.status = COMPLETED`, 결과 저장 |
| `FAILED` | `FAILED` + 사유, FE polling/SSE로 전달 |

---

## 13. 신규 API 체크리스트 (PR 전)

**백엔드**

- [ ] Request DTO `@Valid` + 한국어 validation message
- [ ] Service는 예외 throw (Controller try-catch 없음)
- [ ] 에러 응답: `success` + `errorCode` + `message` (+ `details`)
- [ ] HTTP status가 [../api/convention.md](../api/convention.md)와 일치
- [ ] 성공 204 vs 200 구분
- [ ] Admin → `@PreAuthorize`
- [ ] `../api/specification.md` + `../api/convention.md` 갱신
- [ ] `./gradlew test` 통과

**프론트**

- [ ] `errorCode`로 분기 (message만 의존 X)
- [ ] 401/403/429 처리
- [ ] API 필드명 camelCase = 명세와 동일

---

## 14. 현재 코드 vs 표준 (마이그레이션 백로그)

| # | 항목 | 상태 | 비고 |
| :---: | :--- | :---: | :--- |
| 1 | `ApiErrorResponse.error` → `errorCode` | ✅ | `StandardErrorResponse`로 통일 |
| 2 | Controller inline try-catch | ✅ | Evidence/User/Case → Service + Handler |
| 3 | `AuthException` `errorCode` | ✅ | 401 + `INVALID_CREDENTIALS` 등 |
| 4 | PENDING 로그인 → 403 | ✅ | 401 + `ACCOUNT_PENDING` |
| 5 | `AdminUserPageResponse.items/total` | ✅ | `content/totalElements/totalPages` |
| 6 | Handler 2개 (Global + Signup) | ✅ | `GlobalExceptionHandler` 단일 |
| 7 | `/api/evidences` vs `/api/v1` | ✅ | v1 only (+ `/api/auth/login` legacy) |

**신규 API 규칙:** Service에서 `BusinessException` throw → Controller는 성공 DTO만 반환.

---

## 15. 관련 문서

| 문서 | 내용 |
| :--- | :--- |
| [../api/convention.md](../api/convention.md) | URL, Method, errorCode |
| [naming.md](./naming.md) | camelCase, Enum |
| [../api/specification.md](../api/specification.md) | 엔드포인트별 Request/Response |
| [development.md](./development.md) | 작업 절차 |

---

## 16. 변경 이력

| 날짜 | 내용 |
| :--- | :--- |
| 2026-06-17 | 예외 처리 통일 — BusinessException, StandardErrorResponse, GlobalExceptionHandler 단일화 |
| 2026-06-17 | 초안 — 현재 코드 분석 + 팀 통일 표준 정의 |
