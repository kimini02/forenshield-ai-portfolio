# ForenShield — 회원가입(Signup) API 명세서

> **전체 API:** [specification.md](../api/specification.md) · **회원가입 파트:** 본 문서  
> **공통 API 규칙:** [convention.md](./convention.md)

---

## 1. 공통 규약

### Base URL

```
/api/v1
```

개발: `http://localhost:8080/api/v1` (Spring Boot 기준, 팀 협의 후 확정)

### 인증

| 항목 | 규칙 |
|---|---|
| 방식 | JWT Bearer Token |
| 헤더 | `Authorization: Bearer {accessToken}` |
| 역할 | `USER`, `ADMIN` |

> 회원가입 관련 API는 **모두 Public (인증 불필요)** 입니다. 가입 신청 → 관리자 승인 → 로그인 흐름.

### 공통 에러 응답

```json
{
  "error": "VALIDATION_ERROR",
  "message": "사용자에게 표시할 메시지",
  "details": [
    { "field": "email", "reason": "이미 사용 중인 이메일입니다." }
  ]
}
```

| HTTP | 의미 |
|---|---|
| 400 | 요청 형식/유효성 오류 |
| 401 | 미인증 / 토큰 만료 |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 중복 (아이디, 이메일 등) |
| 429 | 요청 횟수 초과 |
| 500 | 서버 오류 |

### 날짜 형식

- API 응답: ISO 8601 (`2026-06-18T14:30:00`)

---

## 2. 회원가입 (Signup)

### 2.1 가입 신청

| | |
|---|---|
| **Method / Path** | `POST /api/v1/auth/signup` |
| **Auth** | 없음 (Public) |
| **프론트** | `app/signup/page.tsx` |

**Request**

```json
{
  "loginId": "kimminhee",
  "password": "Password123!",
  "displayName": "김민희",
  "organizationType": "POLICE",
  "department": "서울경찰청 사이버수사과",
  "position": "디지털 증거 분석 담당자",
  "email": "kim@example.go.kr",
  "phone": "010-0000-0000",
  "inviteCode": "VF-A3K9-7M2P",
  "agreements": {
    "terms": true,
    "privacy": true,
    "security": true,
    "log": false
  }
}
```

**organizationType enum**

`POLICE` | `PROSECUTION` | `NFS` | `PUBLIC_SECURITY` | `ETC`

**Response `201`**

```json
{
  "userId": "uuid",
  "status": "PENDING",
  "message": "가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다."
}
```

**처리 규칙**

- 가입 신청 직후 `Users.status = PENDING`, `role = ROLE_USER` (고정, 화면에서 선택 없음).
- 승인 전 로그인 불가 (`status = APPROVED` 이후 로그인 가능).
- 가입 성공 시 초대코드 `USED` 처리 + 사용자 연결(`inviteCodeId` / `usedBy`) 기록.
- 필수값 누락·형식 오류 → `400`, 아이디/이메일 중복 → `409`.
- 요청 횟수 초과 → `429`.

---

### 2.2 아이디 중복 확인

| | |
|---|---|
| **Method / Path** | `GET /api/v1/auth/username/check?loginId={loginId}` |
| **Auth** | 없음 (Public) |
| **프론트** | `app/signup/page.tsx` |

**Response `200`**

```json
{ "available": true }
```

- 이미 사용 중이면 `{ "available": false }`.

---

### 2.3 초대코드 유효성 검증

| | |
|---|---|
| **Method / Path** | `POST /api/v1/invite-codes/validate` |
| **Auth** | 없음 (Public) |
| **프론트** | `app/signup/page.tsx` |

**Request**

```json
{ "code": "VF-A3K9-7M2P" }
```

**Response `200`**

```json
{
  "valid": true,
  "expiresAt": "2026-07-01"
}
```

- 유효하지 않거나 만료·사용된 코드 → `{ "valid": false }`.
- 검증만 수행하고 코드를 소모하지 않음 (실제 `USED` 처리는 가입 신청 성공 시점).

---

### 2.4 소속 부서 목록 (자동완성)

| | |
|---|---|
| **Method / Path** | `GET /api/v1/organizations/departments?organizationType=POLICE` |
| **Auth** | 없음 (Public) |
| **프론트** | `app/signup/DepartmentAutocomplete.tsx` |

**Response `200`**

```json
{
  "departments": [
    "서울경찰청 사이버수사과",
    "경기남부경찰청 사이버수사팀"
  ]
}
```

---

## 3. 가입 관련 도메인 규칙 (ERD 연계)

회원가입 구현 시 참고할 `Users` / `InviteCodes` 핵심 컬럼 (자세한 건 [../database/erd.md](../database/erd.md) 참조).

### Users (가입 시 저장)

| 컬럼 | 비고 |
|---|---|
| `loginId` | Unique, Not Null |
| `email` | Unique, Not Null |
| `password` | 암호화 저장 (BCrypt 등) |
| `name` (= `displayName`) | Not Null |
| `phone` | Nullable |
| `organizationType` | OrgType enum, Not Null |
| `department` | Not Null |
| `position` | Nullable |
| `role` | **항상 `ROLE_USER`** (가입 경로 고정) |
| `status` | **`PENDING`** 으로 시작 |
| `inviteCodeId` | 사용한 초대코드 FK |
| `createdAt` / `updatedAt` | 자동 |

### InviteCodes (가입 시 검증·소모)

| 컬럼 | 비고 |
|---|---|
| `code` | Unique |
| `status` | `ACTIVE` → 가입 성공 시 `USED` |
| `expiresAt` | 만료 검증 |
| `usedAt` | 사용 시각 기록 |

### Enum

| Enum | 값 |
|---|---|
| `OrgType` | `POLICE`, `PROSECUTION`, `NFS`, `PUBLIC_SECURITY`, `ETC` |
| `UserStatus` | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` |
| `UserRole` | `ROLE_USER`, `ROLE_ADMIN` |
| `InviteStatus` | `ACTIVE`(=UNUSED), `USED`, `EXPIRED`, `REVOKED` |

## 4. 회원가입 보안 기준

회원가입은 Public API이므로 인증 없이 호출됩니다. 따라서 입력값 검증, 권한 강제, 초대코드 검증, 요청 횟수 제한을 서버에서 반드시 수행합니다.

### 4.1 서버 강제 정책

| 항목 | 기준 |
|---|---|
| `passwordConfirm` | 프론트 검증용. API/DB에 저장하지 않음 |
| `role` | 프론트에서 받지 않음. 가입 시 서버가 항상 `ROLE_USER` 지정 |
| `status` | 프론트에서 받지 않음. 가입 시 서버가 항상 `PENDING` 지정 |
| 비밀번호 저장 | BCrypt 해시로만 저장 |
| 초대코드 검증 | 가입 요청 시 서버에서 최종 검증 |
| 초대코드 소모 | 가입 성공 시점에만 `USED` 처리 |
| 로그인 허용 | 추후 로그인 API에서 `APPROVED` 사용자만 허용 |

### 4.2 입력값 검증

| 필드 | 서버 검증 |
|---|---|
| `loginId` | 필수, 4~50자, 영문/숫자/점/밑줄/하이픈만 허용 |
| `password` | 필수, 8~100자, 대문자/소문자/숫자/특수문자 포함, 공백 불가 |
| `displayName` | 필수, 50자 이하 |
| `organizationType` | 필수, enum 값만 허용 |
| `department` | 필수, 100자 이하 |
| `position` | 선택, 100자 이하 |
| `email` | 필수, 이메일 형식, 100자 이하 |
| `phone` | 선택, 20자 이하, 휴대폰 번호 형식 |
| `inviteCode` | 필수, 50자 이하 |
| `agreements.terms` | 필수, `true` |
| `agreements.privacy` | 필수, `true` |
| `agreements.security` | 필수, `true` |
| `agreements.log` | 선택 |

### 4.3 Rate Limit

회원가입 관련 API는 무차별 대입과 자동화 공격 대상이므로 요청 횟수 제한을 적용합니다.

| API | 제한 |
|---|---|
| `POST /api/v1/auth/signup` | IP 기준 1분 5회 |
| `GET /api/v1/auth/username/check` | IP 기준 1분 20회 |
| `POST /api/v1/invite-codes/validate` | IP 기준 1분 10회 |

제한 초과 시 응답:

```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
  "details": []
}
```

HTTP Status:

```text
429 Too Many Requests
```

### 4.4 현재 구현과 Redis 전환 이유

현재 MVP 구현은 인프라 변경 없이 동작하도록 **서버 메모리 기반 Rate Limit**을 사용합니다.

운영 환경에서는 Redis 기반 Rate Limit으로 전환하는 것을 권장합니다.

Redis를 사용하는 이유는 다음과 같습니다.

```text
1. EKS/EC2에서 서버 인스턴스가 여러 개면 메모리 카운터가 인스턴스별로 분리됨
2. 파드/서버가 재시작되면 메모리 카운터가 사라짐
3. Redis를 사용하면 모든 서버가 같은 요청 카운터를 공유할 수 있음
4. TTL 기반으로 1분 제한 창을 안정적으로 관리할 수 있음
5. 회원가입, 초대코드 검증, 로그인 같은 Public API 보호에 재사용 가능
```

따라서 개발/MVP 단계는 메모리 기반으로 충분하지만, 운영 배포에서는 ElastiCache Redis를 공통 Rate Limit 저장소로 사용하는 것이 안전합니다.

### 4.5 민감정보 로그 정책

다음 값은 서버 로그에 원문으로 남기지 않습니다.

```text
password
Authorization header
JWT
DB password
초대코드 전체 원문
```

초대코드를 로그에 남겨야 하는 경우에는 일부만 표시합니다.

```text
VF-A3K9-****
```

---

## 5. 팀 합의 필요 사항 (회원가입 관련)

| # | 항목 | 현재 프론트 상태 | 제안 |
|---|---|---|---|
| 1 | 로그인 ID | UI "사번", 필드명 `loginId` | API 필드명 `loginId`로 통일 |
| 2 | 이름 필드 | signup `name`, admin `displayName` | API `displayName`으로 통일 |
| 3 | 초대코드 형식 | admin `VF-XXXX-XXXX`, signup 예시 `FSAI-POLICE-2026` | `VF-XXXX-XXXX` 단일 형식 |

---

## 6. 변경 이력

| 날짜 | 브랜치 | 내용 |
|---|---|---|
| 2026-06-09 | `develop` | 최초 작성 — 회원가입 API 파트 분리 |
| 2026-06-09 | `dev` | 회원가입 입력값 검증, Rate Limit, Redis 전환 기준, 민감정보 로그 정책 추가 |
