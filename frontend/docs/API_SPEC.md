# ForenShield Frontend → Backend API 명세서

> **기준 브랜치:** `develop`  
> **작성 목적:** 프론트엔드 UI(mock) 기준으로 백엔드 구현에 필요한 API 계약 정의  
> **현재 프론트 상태:** `fetch`/`axios` 미연동, `sessionStorage`·`localStorage` mock 사용

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
| 역할 | `USER`, `ADMIN` (JWT claim 또는 권한 목록) |
| 로그인 성공 시 | `accessToken` (+ 선택 `refreshToken`) 반환 |

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
| 403 | 권한 없음 (일반 유저가 admin API 호출 등) |
| 404 | 리소스 없음 |
| 409 | 중복 (아이디, 이메일 등) |
| 500 | 서버 오류 |

### 날짜 형식

- API 응답: ISO 8601 (`2026-06-18T14:30:00`)
- 프론트 표시 포맷은 클라이언트 설정(`dateFormat`)으로 처리

---

## 2. 인증 (Auth)

### 2.1 로그인

| | |
|---|---|
| **Method / Path** | `POST /api/v1/auth/login` |
| **Auth** | 없음 (Public) |
| **프론트** | `components/login-form.tsx` |

**Request**

```json
{
  "loginId": "1111",
  "password": "2222"
}
```

> UI 라벨은 "사번"이나 mock은 `loginId` 문자열 비교. **백엔드·프론트 합의 필요:** 사번 vs 아이디 통일.

**Response `200`**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "optional-refresh-token",
  "user": {
    "id": "uuid",
    "loginId": "1111",
    "displayName": "김분석",
    "role": "USER",
    "status": "APPROVED"
  }
}
```

| role | 로그인 후 이동 (프론트) |
|---|---|
| `USER` | `/main` |
| `ADMIN` | `/admin` |

**Response `401`**

```json
{ "error": "INVALID_CREDENTIALS", "message": "아이디 또는 비밀번호가 올바르지 않습니다." }
```

---

### 2.2 로그아웃

| | |
|---|---|
| **Method / Path** | `POST /api/v1/auth/logout` |
| **Auth** | JWT |
| **프론트** | `components/site-header-auth.tsx` |

**Response `204`** — No Content

---

### 2.3 현재 로그인 사용자 조회

| | |
|---|---|
| **Method / Path** | `GET /api/v1/auth/me` |
| **Auth** | JWT |

**Response `200`**

```json
{
  "id": "uuid",
  "loginId": "1111",
  "displayName": "김분석",
  "email": "kim@police.go.kr",
  "department": "사이버수사과",
  "role": "USER",
  "status": "APPROVED"
}
```

---

## 3. 회원가입 (Signup)

### 3.1 가입 신청

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

> 가입 성공 시 초대코드 `USED` 처리 + `usedBy` 기록.

---

### 3.2 아이디 중복 확인

| | |
|---|---|
| **Method / Path** | `GET /api/v1/auth/username/check?loginId={loginId}` |
| **Auth** | 없음 |

**Response `200`**

```json
{ "available": true }
```

---

### 3.3 초대코드 유효성 검증

| | |
|---|---|
| **Method / Path** | `POST /api/v1/invite-codes/validate` |
| **Auth** | 없음 |

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

---

### 3.4 소속 부서 목록 (자동완성)

| | |
|---|---|
| **Method / Path** | `GET /api/v1/organizations/departments?organizationType=POLICE` |
| **Auth** | 없음 (또는 Public) |
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

## 4. 일반 사용자 — 프로필

### 4.1 내 프로필 조회

| | |
|---|---|
| **Method / Path** | `GET /api/v1/users/me` |
| **Auth** | JWT (USER) |
| **프론트** | `app/mypage/edit/page.tsx` |

**Response `200`**

```json
{
  "loginId": "KIM_Forensic",
  "displayName": "김분석",
  "email": "kim@forenshield.go.kr",
  "department": "디지털포렌식센터"
}
```

---

### 4.2 내 프로필 수정

| | |
|---|---|
| **Method / Path** | `PATCH /api/v1/users/me` |
| **Auth** | JWT (USER) |

**Request**

```json
{
  "displayName": "김분석",
  "department": "디지털포렌식센터",
  "currentPassword": "현재비밀번호"
}
```

---

### 4.3 내 비밀번호 변경

| | |
|---|---|
| **Method / Path** | `PATCH /api/v1/users/me/password` |
| **Auth** | JWT (USER) |

**Request**

```json
{
  "currentPassword": "oldPassword",
  "newPassword": "newPassword123!"
}
```

> 프로필 수정과 비밀번호 변경을 **분리**하는 것을 권장 (프론트 `/mypage/edit` 구조와 동일).

---

### 4.4 사용자 설정 (선택 — 2차)

| | |
|---|---|
| **Method / Path** | `GET /api/v1/users/me/settings`, `PATCH /api/v1/users/me/settings` |
| **Auth** | JWT (USER) |
| **프론트** | `lib/user-settings.ts` (현재 localStorage) |

**Settings 스키마**

```json
{
  "theme": "system",
  "dateFormat": "ko-full",
  "analysisCompleteNotification": true,
  "listSort": "newest",
  "listPageSize": 10
}
```

1차에서는 클라이언트 저장만으로도 가능. 서버 푸시 알림 연동 시 필요.

---

## 5. 사건 / 분석 (Cases & Analysis)

### 5.1 내 분석 기록 목록

| | |
|---|---|
| **Method / Path** | `GET /api/v1/cases/me` |
| **Auth** | JWT (USER) |
| **프론트** | `app/mypage/page.tsx` |

**Query (선택)**

| Param | 값 | 기본 |
|---|---|---|
| `sort` | `newest` \| `status` | `newest` |
| `page` | number | 0 |
| `size` | 10 \| 20 \| 50 | 10 |

**Response `200`**

```json
[
  {
    "caseId": "c4b37830-3653-4b23-b17b-5241b3783038",
    "caseName": "가세연 녹취록 딥페이크 의혹 사건",
    "status": "PROCESSING",
    "createdAt": "2026-06-18T14:30:00",
    "evidenceCount": 2
  }
]
```

**status enum:** `PENDING` | `PROCESSING` | `COMPLETED` | `FAILED`

---

### 5.2 사건 상세

| | |
|---|---|
| **Method / Path** | `GET /api/v1/cases/{caseId}` |
| **Auth** | JWT (본인 사건 또는 ADMIN) |
| **프론트** | `app/cases/[id]/page.tsx` (현재 스텁) |

**Response `200`**

```json
{
  "caseId": "uuid",
  "caseName": "사건명",
  "status": "COMPLETED",
  "createdAt": "2026-06-18T14:30:00",
  "evidences": [
    {
      "evidenceId": "uuid",
      "fileName": "interview_clip_04.mp4",
      "mediaType": "VIDEO",
      "hash": "sha256:...",
      "verdict": "MANIPULATED",
      "confidence": 96
    }
  ],
  "cocLogs": []
}
```

---

### 5.3 파일 업로드 및 분석 요청

| | |
|---|---|
| **Method / Path** | `POST /api/v1/analyses` |
| **Auth** | JWT (USER) |
| **Content-Type** | `multipart/form-data` |
| **프론트** | `components/upload-panel.tsx` |

**Form fields**

| Field | Type | 설명 |
|---|---|---|
| `files` | File[] | 증거 파일 (최대 2GB/파일) |
| `mediaKind` | string | `all` \| `audio` \| `video` \| `image` |
| `caseName` | string | 선택 — 없으면 파일명 기반 자동 생성 |

**Response `202`**

```json
{
  "caseId": "uuid",
  "analyses": [
    {
      "analysisId": "uuid",
      "fileName": "interview_clip_04.mp4",
      "status": "PROCESSING"
    }
  ]
}
```

---

### 5.4 분석 상태/결과 조회

| | |
|---|---|
| **Method / Path** | `GET /api/v1/analyses/{analysisId}` |
| **Auth** | JWT (USER) |

**Response `200`**

```json
{
  "analysisId": "uuid",
  "status": "COMPLETED",
  "fileName": "interview_clip_04.mp4",
  "riskScore": 96,
  "verdict": "MANIPULATED",
  "confidence": 96
}
```

**verdict enum:** `AUTHENTIC` | `SUSPICIOUS` | `MANIPULATED`

---

### 5.5 최근 분석 내역

| | |
|---|---|
| **Method / Path** | `GET /api/v1/analyses/recent?limit=3` |
| **Auth** | JWT (USER) |
| **프론트** | `components/recent-analyses.tsx` |

**Response `200`**

```json
[
  {
    "id": "CASE-2026-0412",
    "name": "interview_clip_04.mp4",
    "kind": "video",
    "verdict": "manipulated",
    "confidence": 96,
    "time": "2026-06-09T10:00:00"
  }
]
```

---

### 5.6 증거 메타데이터

| | |
|---|---|
| **Method / Path** | `GET /api/v1/evidence/{evidenceId}/metadata` |
| **Auth** | JWT (USER) |
| **프론트** | `components/metadata-info.tsx` |

**Response `200`**

```json
{
  "fileName": "interview_clip_04.mp4",
  "fileSize": "128.4 MB",
  "fileType": "video/mp4",
  "hash": "a3f2b8c1...",
  "uploadDate": "2026-06-09T09:15:00",
  "device": "Unknown"
}
```

---

## 6. 관리자 (Admin)

> 모든 `/api/v1/admin/**` 엔드포인트는 **JWT + ADMIN 역할** 필수.

### 6.1 대시보드 통계

| | |
|---|---|
| **Method / Path** | `GET /api/v1/admin/dashboard/stats` |
| **프론트** | `app/admin/page.tsx` |

**Response `200`**

```json
{
  "pendingUsers": 2,
  "totalUsers": 5,
  "todayLogs": 12,
  "unusedInviteCodes": 1,
  "cocLogs": 6
}
```

---

### 6.2 계정 목록

| | |
|---|---|
| **Method / Path** | `GET /api/v1/admin/users` |
| **프론트** | `app/admin/users/page.tsx` |

**Query**

| Param | 설명 |
|---|---|
| `search` | 아이디, 이름, 이메일 검색 |
| `status` | `PENDING` \| `APPROVED` \| `REJECTED` |
| `page` | 페이지 번호 (0-based) |
| `size` | 페이지 크기 (기본 10) |

**Response `200`**

```json
{
  "items": [
    {
      "id": "1",
      "username": "admin_kim",
      "displayName": "김관리",
      "email": "kim@police.go.kr",
      "department": "사이버수사과",
      "joinedAt": "2026-06-01",
      "status": "PENDING"
    }
  ],
  "total": 5,
  "page": 0,
  "size": 10
}
```

---

### 6.3 가입 승인 / 반려

| | |
|---|---|
| **승인** | `POST /api/v1/admin/users/{userId}/approve` |
| **반려** | `POST /api/v1/admin/users/{userId}/reject` |

**Response `200`**

```json
{ "userId": "uuid", "status": "APPROVED" }
```

> CoC/ADMIN 로그 자동 기록 권장.

---

### 6.4 계정 정보 수정 (관리자)

| | |
|---|---|
| **Method / Path** | `PATCH /api/v1/admin/users/{userId}` |
| **프론트** | `app/admin/_components/edit-user-dialog.tsx` |

**Request**

```json
{
  "displayName": "이포렌",
  "email": "lee@nfs.go.kr",
  "department": "디지털분석팀"
}
```

---

### 6.5 계정 비밀번호 재설정 (관리자 → 타 사용자)

| | |
|---|---|
| **Method / Path** | `PATCH /api/v1/admin/users/{userId}/password` |

**Request**

```json
{ "newPassword": "TempPass123!" }
```

> 사용자 **현재 비밀번호 불필요**. CoC 로그 필수 기록.

---

### 6.6 계정 삭제

| | |
|---|---|
| **Method / Path** | `DELETE /api/v1/admin/users/{userId}` |

**Response `204`**

> soft delete 권장 (`status: DELETED`).

---

### 6.7 생성코드 목록

| | |
|---|---|
| **Method / Path** | `GET /api/v1/admin/invite-codes` |
| **프론트** | `app/admin/invite-codes/page.tsx` |

**Response `200`**

```json
[
  {
    "id": "c1",
    "code": "VF-A3K9-7M2P",
    "createdAt": "2026-06-01",
    "expiresAt": "2026-07-01",
    "status": "UNUSED",
    "usedBy": null
  }
]
```

**status enum:** `UNUSED` | `USED` | `EXPIRED`

---

### 6.8 생성코드 발급

| | |
|---|---|
| **Method / Path** | `POST /api/v1/admin/invite-codes` |

**Request (선택)**

```json
{ "expiresInDays": 30 }
```

**Response `201`**

```json
{
  "id": "uuid",
  "code": "VF-B8N1-4Q6R",
  "createdAt": "2026-06-09",
  "expiresAt": "2026-07-09",
  "status": "UNUSED"
}
```

---

### 6.9 로그 / CoC 조회

| | |
|---|---|
| **Method / Path** | `GET /api/v1/admin/logs` |
| **프론트** | `app/admin/logs/page.tsx` |

**Query**

| Param | 설명 |
|---|---|
| `category` | `AUTH` \| `ANALYSIS` \| `ADMIN` \| `COC` (미지정 시 전체) |
| `department` | 부서 필터 |
| `search` | 행위, 사용자, 상세 검색 |
| `from` / `to` | 기간 필터 (ISO date) |
| `page` / `size` | 페이지네이션 (기본 size=8) |

**Response `200`**

```json
{
  "items": [
    {
      "id": "l1",
      "timestamp": "2026-06-09T09:12:00",
      "category": "AUTH",
      "actor": "admin_kim",
      "actorId": "1",
      "department": "사이버수사과",
      "action": "로그인",
      "detail": "내부망 접속"
    }
  ],
  "total": 12,
  "departments": ["사이버수사과", "디지털분석팀", "시스템"]
}
```

---

### 6.10 로그 CSV보내기 (2차)

| | |
|---|---|
| **Method / Path** | `GET /api/v1/admin/logs/export?format=csv&...` |
| **Response** | `text/csv` 파일 다운로드 |

---

### 6.11 관리자 본인 프로필

| | |
|---|---|
| **조회** | `GET /api/v1/admin/me` |
| **수정** | `PATCH /api/v1/admin/me` |
| **프론트** | `app/admin/profile/page.tsx` |

**PATCH Request**

```json
{
  "username": "admin_kim",
  "displayName": "김관리",
  "email": "kim@police.go.kr",
  "department": "사이버수사과",
  "phone": "010-1234-5678"
}
```

> `username` 변경 시 중복 확인(`409`) 필요. `role`은 읽기 전용.

---

### 6.12 관리자 본인 비밀번호 변경

| | |
|---|---|
| **Method / Path** | `PATCH /api/v1/admin/me/password` |

**Request**

```json
{
  "currentPassword": "oldAdminPass",
  "newPassword": "newAdminPass123!"
}
```

> 타 사용자 재설정(6.5)과 달리 **현재 비밀번호 필수**.

---

## 7. API ↔ 프론트 화면 매핑

| 화면 | 경로 | API |
|---|---|---|
| 로그인 | `/login` | `POST /auth/login` |
| 회원가입 | `/signup` | `POST /auth/signup`, validate/check APIs |
| 메인/업로드 | `/main` | `POST /analyses`, `GET /analyses/recent` |
| 내 분석 기록 | `/mypage` | `GET /cases/me` |
| 사건 상세 | `/cases/{id}` | `GET /cases/{id}` |
| 개인정보 수정 | `/mypage/edit` | `GET/PATCH /users/me`, `PATCH /users/me/password` |
| 설정 드롭다운 | 헤더 | (1차 localStorage, 2차 `/users/me/settings`) |
| 관리자 대시보드 | `/admin` | `GET /admin/dashboard/stats` |
| 계정 관리 | `/admin/users` | `GET/PATCH/DELETE /admin/users`, approve/reject |
| 생성코드 | `/admin/invite-codes` | `GET/POST /admin/invite-codes` |
| 로그 | `/admin/logs` | `GET /admin/logs` |
| 관리자 내 정보 | `/admin/profile` | `GET/PATCH /admin/me`, `PATCH /admin/me/password` |

---

## 8. 구현 우선순위 (권장)

### 1차 — MVP (팀 데모 / 연동 테스트)

1. `POST /auth/login`, `GET /auth/me`, `POST /auth/logout`
2. `POST /auth/signup`, `POST /invite-codes/validate`
3. `GET /cases/me`, `GET /cases/{id}` (상세 최소 필드)
4. `POST /analyses`, `GET /analyses/{id}`
5. `GET /admin/users`, approve/reject, `GET /admin/invite-codes`, `POST /admin/invite-codes`

### 2차 — 운영 기능

6. Admin logs, dashboard stats
7. Admin user edit/delete/password reset
8. Admin profile + password
9. User profile edit (`/mypage/edit`)

### 3차 — 고도화

10. Log CSV export, notification settings, polling/WebSocket for analysis progress

---

## 9. 팀 합의 필요 사항

| # | 항목 | 현재 프론트 상태 | 제안 |
|---|---|---|---|
| 1 | 로그인 ID | UI "사번", 필드명 `loginId` | API 필드명 `loginId`로 통일 |
| 2 | 이름 필드 | signup `name`, admin `displayName` | API `displayName`으로 통일 |
| 3 | 초대코드 형식 | admin `VF-XXXX-XXXX`, signup 예시 `FSAI-POLICE-2026` | `VF-XXXX-XXXX` 단일 형식 |
| 4 | 업로드 → 사건 | 업로드는 `analysisId`, 마이페이지는 `caseId` | 업로드 시 `case` 자동 생성 후 `caseId` 반환 |
| 5 | 페이지네이션 | admin 0/1-based 혼재 가능 | **0-based page** 로 통일 |
| 6 | 삭제 방식 | UI "삭제" | soft delete + `DELETED` 상태 권장 |

---

## 10. 변경 이력

| 날짜 | 브랜치 | 내용 |
|---|---|---|
| 2026-06-09 | `develop` | 최초 작성 — 프론트 mock/UI 기준 |
