# 백엔드 팀 가이드

> **스택:** Spring Boot 3 · JPA · PostgreSQL · JWT  
> **경로:** `backend/backend-forensic/` (**본 레포**)  
> **진입:** [../AGENTS.md](../AGENTS.md)

---

## 1. 필독 문서

1. [../rule.md](../rule.md)
2. [../database/erd.md](../database/erd.md)
3. [../api/specification.md](../api/specification.md)
4. [../guides/implementation-standards.md](../guides/implementation-standards.md)
5. [../guides/development.md](../guides/development.md)
6. [../guides/backend-file-map.md](../guides/backend-file-map.md) — 소스 파일별 역할 맵
7. [../requirements/traceability.md](../requirements/traceability.md)

---

## 2. 패키지 구조

```
com.example.demo
├── controller/       # HTTP only — try-catch 금지
├── service/          # @Transactional, BusinessException throw
│   └── blockchain/client/  # BlockchainAnchorClient (simulated · http)
├── repository/       # JPA
├── domain/           # Entity, Enum
├── dto/              # Request/Response
├── security/         # JWT, AuthUserResolver, SecurityErrorResponses
├── exception/        # BusinessException, GlobalExceptionHandler
├── util/             # JsonPayloadWriter, CaseKeyNormalizer, MerkleTreeUtil
├── messaging/        # RabbitMQ enqueue/consume
└── config/           # Security, S3, etc.
```

---

## 3. 예외·응답 (통일 완료)

```java
// Service
throw new BusinessException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다.");

// Controller — 성공 DTO만 반환
@GetMapping("/stats")
public EvidenceStatsResponse stats() { ... }
```

에러 JSON: `StandardErrorResponse` — `success`, `errorCode`, `message`, `details?`  
401/403도 동일 형식 (`SecurityErrorResponses` · `RestAuthenticationEntryPoint` · `RestAccessDeniedHandler`).

→ [implementation-standards.md §5](../guides/implementation-standards.md)

---

## 4. API Prefix

| 표준 | Legacy |
| :--- | :--- |
| `/api/v1/evidences/**` | — (alias 제거됨, 2026-06) |
| `/api/v1/admin/**` | — |
| `/api/auth/login` | 유지 (유일한 legacy) |

신규 API는 **반드시 `/api/v1`**.

---

## 5. CoC (CustodyLogs) 기록

| 이벤트 | 시점 |
| :--- | :--- |
| `LOGIN` | 로그인 성공 |
| `EVIDENCE_UPLOADED` | 업로드·해시 완료 |
| `ANALYSIS_REQUESTED` | AnalysisRequest 생성 |
| `ANALYSIS_COMPLETED` / `FAILED` | AI 결과 수신 |

해시 체인: `previousLogHash` → `currentLogHash` ([RQ-REQ-051](../requirements/index.md))

---

## 6. 상태 Enum (ERD = 코드)

| Entity | Field | Values |
| :--- | :--- | :--- |
| Users | status | PENDING, APPROVED, REJECTED, SUSPENDED |
| AnalysisRequests | status | QUEUED, ANALYZING, COMPLETED, FAILED |
| AnalysisResults | riskLevel | LOW, MEDIUM, HIGH |

---

## 7. 테스트·실행

```bash
./gradlew test
./gradlew bootRun
```

Swagger: `http://localhost:8080/swagger-ui.html`

---

## 8. 연동

| 대상 | 문서 |
| :--- | :--- |
| S3 업로드 | [integrations/s3.md](../integrations/s3.md) |
| AI 큐 | [integrations/rabbitmq.md](../integrations/rabbitmq.md) |
| AI JSON | [integrations/ai-json.md](../integrations/ai-json.md) |

---

## 9. PR 체크list

- [ ] RQ-ID / FN-BE-ID
- [ ] `./gradlew test`
- [ ] specification.md + convention.md 갱신
- [ ] ERD 변경 시 erd.md 선행 수정
- [ ] Admin → `@PreAuthorize("hasRole('ADMIN')")`
- [ ] traceability.md 상태 emoji

---

## 10. Gap 백로그 (BE)

[specification.md §0.4](../api/specification.md): PDF · Compare · Notifications · Settings · Blockchain trend API
