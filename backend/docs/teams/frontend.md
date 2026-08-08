# 프론트엔드 팀 가이드

> **스택:** Next.js 16 · React 19 · Tailwind v4 · shadcn/ui  
> **경로:** `frontend/frontend-deepfake/`  
> **진입:** [../AGENTS.md](../AGENTS.md)

---

## 1. 필독 문서 (순서)

1. [../rule.md](../rule.md)
2. [../architecture/system-overview.md](../architecture/system-overview.md)
3. [../api/specification.md §2](../api/specification.md) — **연동 API 정본**
4. [../guides/implementation-standards.md §6·§11](../guides/implementation-standards.md)
5. [../api/convention.md](../api/convention.md)
6. 담당 화면 RQ → [../requirements/index.md](../requirements/index.md)

---

## 2. 라우팅 (명세 기준)

| 경로 | 용도 | RQ (예) |
| :--- | :--- | :--- |
| `/login` | 로그인 | RQ-LOGIN-* |
| `/signup` | 회원가입 | RQ-SIGNUP-* |
| `/main` | 사용자 대시보드 | RQ-DSH-* |
| `/mypage` | 마이페이지 | RQ-MY-* |
| `/admin/*` | 관리자 | RQ-ADMIN-* |

UI 컴포넌트 상세는 **기능명세서 Excel UI상세 시트** 참조.

---

## 3. 인증 (sessionStorage)

로그인 성공 (`POST /api/auth/login`):

```typescript
sessionStorage.setItem("accessToken", token);
sessionStorage.setItem("userId", String(userId));
sessionStorage.setItem("loginId", loginId);
sessionStorage.setItem("name", name);
sessionStorage.setItem("role", role); // ROLE_USER | ROLE_ADMIN
```

API 호출:

```http
Authorization: Bearer {accessToken}
```

- 401 + `UNAUTHORIZED` → 로그아웃 후 `/login`
- 401 + `ACCOUNT_PENDING` → 메시지 표시 (로그인 페이지 유지)

---

## 4. API Base URL

| 환경 변수 | 값 예 |
| :--- | :--- |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` |

- 표준: `{BASE}/api/v1/...`
- 예외: 로그인 `{BASE}/api/auth/login`
- 증거: `{BASE}/api/v1/evidences/...` (v1 only)

---

## 5. 에러 처리 (필수)

```typescript
type ApiError = {
  success: false;
  errorCode: string;
  message: string;
  details?: { field: string; reason: string }[];
};
```

- **`errorCode`로 분기** (message만 의존 금지)
- Validation → `details[]` 필드별 표시
- Admin 목록: `content`, `totalElements`, `totalPages` (~~items/total~~)

fetch 래퍼 예: [implementation-standards.md §11](../guides/implementation-standards.md)

---

## 6. 주요 API 매핑 (화면 → API)

| 화면 | API | Response 핵심 필드 |
| :--- | :--- | :--- |
| 대시보드 카드 | `GET /api/v1/evidences/stats` | `totalAnalysisCount`, `deepfakeDetectedCount`, `completedCount`, `inProgressCount` |
| 파일 업로드 | `POST /api/v1/evidences/upload` | `evidenceId`, `hashValue` |
| 분석 시작 | `POST /api/v1/evidences/analyze` | `{ evidenceId }` 또는 `{ evidenceIds, caseName? }` |
| 분석 진행 | `GET .../analysis-status` | `status`, `progressPercent` |
| 사건 상세 | `GET /api/v1/cases/{caseId}` | `CaseDetailResponse` |
| 분석 이력 | `GET /api/v1/mypage/analysis-history` | `content`, `totalElements` |
| 관리자 사용자 | `GET /api/v1/admin/users` | `content`, `totalElements` |
| 관리자 통계 분석 | `GET /api/v1/admin/dashboard/analysis-stats` | `weeklyTotalCount`, `deepfakeDetectionRate`, `weeklyPoints`, `riskDistribution` |

전체: [specification.md](../api/specification.md)

---

## 7. PR 체크리스트

- [ ] RQ-ID / FN-FE-ID 명시
- [ ] Mock 제거 시 필드명 = specification.md
- [ ] `errorCode` 분기
- [ ] Admin·User 라우트 가드 (RQ-ADMIN-114~116, RQ-COM-013)
- [ ] 로딩·에러 UI

---

## 8. 미구현 BE API (UI만 먼저 만들지 말 것)

Compare · PDF · Notifications · Settings — [specification.md §0.4](../api/specification.md)

BE 스펙 확정 전 Mock API 자체 생성 금지 (팀 합의 필요).
