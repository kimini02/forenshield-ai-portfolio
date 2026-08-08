# 명명 규칙 (Naming Convention)

> **적용:** Java · TypeScript · SQL · Git · 문서

---

## 1. Git

| 대상 | 규칙 | 예 |
| :--- | :--- | :--- |
| 브랜치 | `feature/{파트}-{요약}` | `feature/be-evidence-upload` |
| 커밋 | Conventional Commits | `feat: add custody log on upload` |
| PR | `[RQ-ID] 요약` | `[RQ-REQ-048] S3 WORM upload` |

---

## 2. RQ / FN / 문서

| 대상 | 규칙 | 예 |
| :--- | :--- | :--- |
| 요구사항 | `RQ-{영역}-{번호}` | `RQ-DTL-057` |
| 기능 | `FN-{영역}-{번호}-{FE\|BE\|AI\|INF}` | `FN-REQ-049-BE` |
| Markdown | kebab-case 파일명 | `specification.md`, `convention.md` |

---

## 3. Java (백엔드)

| 대상 | 규칙 | 예 |
| :--- | :--- | :--- |
| 패키지 | lowercase | `com.example.demo.service` |
| Class | PascalCase | `EvidenceController` |
| Method | camelCase | `uploadEvidence` |
| Constant | UPPER_SNAKE | `MAX_FILE_SIZE` |
| Entity | 단수 PascalCase | `Evidence`, `AnalysisRequest` |
| Repository | `{Entity}Repository` | `EvidenceRepository` |
| DTO Request | `{Action}{Resource}Request` | `StartAnalysisRequest` |
| DTO Response | `{Resource}Response` | `FileUploadResponse` |
| Enum | PascalCase type, UPPER values | `UserStatus.APPROVED` |

### 3.1 Controller 매핑

```java
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    @GetMapping("/{userId}")
    public AdminUserItemResponse getUser(@PathVariable Long userId) { ... }
}
```

---

## 4. TypeScript (프론트)

| 대상 | 규칙 | 예 |
| :--- | :--- | :--- |
| 컴포넌트 파일 | PascalCase.tsx | `DepartmentAutocomplete.tsx` |
| hook / util | camelCase.ts | `useAuth.ts` |
| API 타입 | PascalCase | `LoginResponse` |
| 상수 | UPPER_SNAKE | `API_BASE_URL` |
| 라우트 폴더 | kebab | `app/analysis-history/` |

---

## 5. REST API

| 대상 | 규칙 | 예 |
| :--- | :--- | :--- |
| Path segment | kebab-case, 복수 | `/api/v1/invite-codes` |
| Path param | camelCase | `{evidenceId}` |
| Query | camelCase | `?organizationType=POLICE` |
| JSON field | camelCase | `displayName`, `hashValue` |
| errorCode | UPPER_SNAKE | `FILE_NOT_FOUND` |

---

## 6. Database (ERD v3)

| 대상 | 규칙 | 예 |
| :--- | :--- | :--- |
| Table | PascalCase (문서) / snake (SQL) | `Users` → `users` |
| Column | camelCase (JPA) / snake (SQL) | `originalHashValue` |
| PK | `{entity}Id` | `evidenceId`, `userId` |
| FK | 참조 컬럼명 동일 | `userId` |
| Enum column | VARCHAR + CHECK 또는 PostgreSQL ENUM | `status` |

### 6.1 핵심 Enum (문자열 값 고정)

```
UserStatus:     PENDING | APPROVED | REJECTED | SUSPENDED
UserRole:       ROLE_USER | ROLE_ADMIN
EvidenceStatus: UPLOADED | DELETED
AnalysisStatus: QUEUED | ANALYZING | COMPLETED | FAILED
FileType:       VIDEO (MVP 업로드) · IMAGE | AUDIO (DB enum 잔존, 미사용)
RiskLevel:      LOW | MEDIUM | HIGH
InviteStatus:   ACTIVE | USED | EXPIRED | REVOKED
OrgType:        POLICE | PROSECUTION | NFS | PUBLIC_SECURITY | ETC
```

---

## 7. S3 Object Key

[../integrations/s3.md](../integrations/s3.md)

```
cases/{caseId}/{evidenceId}/original/{fileName}
cases/{caseId}/{evidenceId}/copy/{fileName}
cases/{caseId}/{evidenceId}/reports/{reportId}.pdf
```

- 소문자, `/` 구분, 공백·한글 파일명은 업로드 시 sanitize

---

## 8. RabbitMQ

| 대상 | 규칙 | 예 |
| :--- | :--- | :--- |
| Exchange | dot.notation | `ai.analysis.exchange` |
| Queue | dot.notation | `backend.ai.result.queue` |
| Routing key | dot.lowercase | `analyze.video` |

---

## 9. 환경 변수

| 규칙 | 예 |
| :--- | :--- |
| UPPER_SNAKE | `JWT_SECRET`, `AWS_S3_BUCKET` |
| 프론트 public | `NEXT_PUBLIC_API_URL` |

---

## 10. 금지·주의

- `files` 테이블명 (레거시) vs `Evidences` (ERD v3) 혼용 시 **ERD v3 우선**
- `case_id` (UUID 레거시) vs `caseNumber` 문자열 (MVP) — **MVP는 caseNumber/caseName**
- 한 API에서 `id` / `userId` / `loginId` 혼동 — 명세 필드명 유지

---

## 11. 관련 문서

- [../database/erd.md](../database/erd.md)
- [../api/convention.md](../api/convention.md)
- [../rule.md](../rule.md)
