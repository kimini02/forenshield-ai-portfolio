# ForenShield 개발 가이드

> **대상:** 프론트 · 백엔드 · AI · 인프라 전체  
> **상위 규칙:** [../rule.md](../rule.md)

본 문서는 **기능 하나를 구현할 때 따라야 할 표준 절차**입니다.

---

## 1. 작업 시작 전 (5분)

1. Jira/Notion 또는 팀 보드에서 담당 **RQ-ID** 확인
2. [requirements/index.md](../requirements/index.md)에서 요구사항 **내용·중요도** 읽기
3. [requirements/traceability.md](../requirements/traceability.md)에서 본인 파트 **FN-ID** 확인
4. API/DB 관련이면 [api/specification.md](../api/specification.md), [database/erd.md](../database/erd.md) 확인
5. `develop` 최신화 후 `feature/{영역}-{요약}` 브랜치 생성

---

## 2. 설계 (구현 전 필수)

### 2.1 체크리스트

| # | 질문 | 참고 문서 |
| :---: | :--- | :--- |
| 1 | 어떤 RQ/FN을 만족하는가? | RTM |
| 2 | API가 이미 정의되어 있는가? | api/specification |
| 3 | DB 테이블·컬럼이 ERD v3와 일치하는가? | database/erd |
| 4 | CoC(보관연속성) 로그가 필요한가? | ERD § CustodyLogs |
| 5 | AI/인프라와 주고받는 JSON·큐 이름은? | integrations/ai-json, rabbitmq |
| 6 | 프론트 라우트·화면 ID는? | 기능명세서 UI상세 시트 |

### 2.2 API를 새로 만들 때

1. [api/convention.md](../api/convention.md)에 따라 Method·Path·DTO 설계
2. `api/specification.md`에 **먼저** 초안 추가 (Review 가능 상태)
3. FE 담당자와 Request/Response 필드명 합의 (`camelCase`)
4. 구현 → 테스트 → 문서 확정을 **한 PR**에 묶기

### 2.3 DB 변경 시

1. `database/erd.md`와 불일치하면 **ERD 먼저 수정**
2. Flyway/SQL: `src/main/resources/db/schema/` (팀 convention 따름)
3. `Users.status`, `AnalysisRequests.status` 등 **enum 값은 문서와 코드 동일** 유지

---

## 3. 백엔드 구현 규칙 (Spring Boot)

### 3.1 패키지 구조

```
com.example.demo
├── controller/     # HTTP 입출력만
├── service/        # 비즈니스·트랜잭션
├── repository/     # JPA
├── domain/         # Entity, Enum
├── dto/            # Request/Response (record 또는 class)
├── security/       # JWT, AuthUserResolver
└── exception/      # 도메인 예외
```

### 3.2 레이어 규칙

- **Controller:** `@Valid` 검증, `ResponseEntity`, HTTP 상태 코드만 결정
- **Service:** `@Transactional`, CustodyLog 기록, 외부(S3·Redis·RabbitMQ) 호출
- **Repository:** JPQL/메서드 쿼리, 비즈니스 로직 금지

### 3.3 CoC (CustodyLogs) 기록 시점

| 이벤트 | 기록 시점 |
| :--- | :--- |
| `LOGIN` | 로그인 성공 |
| `EVIDENCE_UPLOADED` | 업로드·해시 생성 완료 |
| `HASH_CREATED` | SHA-256 확정 |
| `ANALYSIS_REQUESTED` | AnalysisRequests 생성 |
| `ANALYSIS_COMPLETED` / `FAILED` | AI 결과 수신 |
| `REPORT_GENERATED` | PDF 저장 |

해시 체인: `previousLogHash` + 현재 로그 내용 → `currentLogHash` ([RQ-REQ-051](../requirements/index.md))

### 3.4 증거·분석 상태 (정본)

| 구분 | 필드 | 값 |
| :--- | :--- | :--- |
| 계정 | `Users.status` | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` |
| 권한 | `Users.role` | `ROLE_USER`, `ROLE_ADMIN` |
| 증거 | `Evidences.status` | `UPLOADED`, `DELETED` |
| 분석 | `AnalysisRequests.status` | `QUEUED`, `ANALYZING`, `COMPLETED`, `FAILED` |

- 분석 상태는 **AnalysisRequests에만** 관리 (Evidences에 중복 금지)
- `COMPLETED` 후 재분석 UI/API **MVP 불허**
- `FAILED` 재시도 → **새 AnalysisRequests row**

---

## 4. 프론트엔드 구현 규칙

### 4.1 라우팅 (명세 기준)

| 경로 | 용도 |
| :--- | :--- |
| `/login` | 로그인 |
| `/signup` | 회원가입 |
| `/main` | 일반 사용자 대시보드 |
| `/admin` | 관리자 홈 |
| (기타) | 기능명세서 UI상세 시트 참조 |

### 4.2 인증 저장 (RQ-LOGIN-024)

로그인 성공 시 **sessionStorage** (탭 종료 시 세션 종료):

- `accessToken` (JWT)
- `userId`, `loginId`, `name`, `role`

API 호출: `Authorization: Bearer {accessToken}`

### 4.3 API 클라이언트

- Base URL: 환경변수 `NEXT_PUBLIC_API_URL` + `/api/v1` (로그인 legacy `/api/auth/login` 예외)
- 에러: [api/convention.md §5](../api/convention.md) `errorCode`로 분기
- 응답·예외 통일: [implementation-standards.md](./implementation-standards.md)

---

## 5. AI 워커 구현 규칙

1. 입력: [../integrations/ai-json.md](../integrations/ai-json.md) Analysis Request
2. 출력: 동 문서 Analysis Response (`riskScore`, `results[]`)
3. 큐: [../integrations/rabbitmq.md](../integrations/rabbitmq.md) routing key (`analyze.video` 등)
4. S3 **copy/** 경로만 읽기 (original WORM 수정 금지)
5. 실패 시 status `FAILED` + 사유 문자열

---

## 6. 테스트·PR 전 체크리스트

### 백엔드

- [ ] `./gradlew test` 통과
- [ ] 새 API → Swagger `@Operation` 설명
- [ ] Public API → Rate limit·validation ([../api/signup.md](../api/signup.md) 참고)
- [ ] Admin API → `@PreAuthorize("hasRole('ADMIN')")`
- [ ] 에러 JSON → [implementation-standards.md](./implementation-standards.md) (`success` + `errorCode` + `message`)
- [ ] RTM 상태 emoji 갱신 (해당 FN)

### 프론트

- [ ] 비로그인 시 보호 라우트 차단 (RQ-COM-013)
- [ ] API mock 제거 시 명세 필드명과 일치
- [ ] `errorCode`로 분기 ([implementation-standards.md §11](./implementation-standards.md))
- [ ] 로딩·에러 UI (RQ-LOGIN-028 등)

### 공통

- [ ] RQ-ID / FN-ID PR에 기재
- [ ] 관련 Markdown 갱신
- [ ] 시크릿·`.env` 미포함 확인

---

## 7. 코드 리뷰 기준

리뷰어는 아래를 확인합니다.

1. **명세 일치:** RQ 내용과 동작이 같은가?
2. **경계:** 다른 파트 책임을 침범하지 않았는가?
3. **보안:** JWT·권한·입력 검증·로그에 비밀번호/토큰 미포함
4. **무결성:** CoC·해시·WORM 정책 위반 없음
5. **문서:** API/ERD 동기화
6. **응답 형식:** [implementation-standards.md](./implementation-standards.md) 위반 없음

---

## 8. 자주 하는 실수 (금지)

| 실수 | 올바른 방법 |
| :--- | :--- |
| `/api/evidences` 경로 사용 (제거됨) | `/api/v1/evidences/**`만 사용 — [../api/convention.md](../api/convention.md) |
| 가입 시 `role=ADMIN` 허용 | 서버에서 항상 `ROLE_USER` |
| 증거 status에 `ANALYZING` 저장 | `AnalysisRequests.status` 사용 |
| AI JSON 필드 snake_case 임의 변경 | 명세 camelCase 유지 |
| ERD 없이 컬럼 추가 | ERD v3 먼저 수정 |

---

## 9. 연락·에스컬레이션

| 이슈 | 담당 |
| :--- | :--- |
| API 계약 변경 | BE + FE 동시 PR |
| ERD 변경 | BE + DBA/리드 리뷰 |
| AI 스키마 변경 | AI + BE |
| 인프라(S3·Redis) | INF + BE |

---

## 10. 관련 문서

- [rule.md](../rule.md)
- [requirements/index.md](../requirements/index.md)
- [requirements/traceability.md](../requirements/traceability.md)
- [api/convention.md](../api/convention.md)
- [api/specification.md](../api/specification.md)
