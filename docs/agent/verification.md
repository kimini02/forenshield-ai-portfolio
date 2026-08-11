# Verification Guide

모든 명령을 매번 실행하는 대신 변경한 구성 요소와 위험에 맞는 최소 검증을 선택합니다. 실행하지 않은 검증을 통과했다고 기록하지 않습니다.

## Frontend

```bash
cd frontend
pnpm install
pnpm lint
pnpm build
```

- 의존성이 이미 설치되어 있으면 `pnpm install`은 생략할 수 있습니다.
- 화면 변경은 loading, error, empty, success와 권한 없음 상태를 확인합니다.
- API 변경은 실제 응답 타입, `errorCode`, 인증 옵션과 mock/real 경계를 확인합니다.

## Backend

```bash
cd backend
./gradlew test
./gradlew compileJava
```

- 비즈니스 규칙은 service 또는 controller 수준의 회귀 테스트를 추가합니다.
- 권한 변경은 허용 사례뿐 아니라 다른 역할·기관·부서의 거부 사례를 포함합니다.
- DB 변경은 migration과 기존 데이터 호환성을 확인합니다.
- API 변경은 HTTP status와 표준 에러 응답을 함께 검증합니다.

## AI Server

현재 AI Server에는 별도 자동 테스트 suite가 없으므로 최소한 import와 endpoint를 확인합니다.

```bash
cd ai
python3 -m compileall app
uvicorn app.main:app --port 8000
```

서버 실행 후 다른 터미널에서 확인합니다.

```bash
curl http://localhost:8000/health
```

분석 schema나 로직을 변경했다면 정상 요청뿐 아니라 잘못된 파일·해시, 누락 필드, timeout과 내부 오류 사례를 자동 테스트로 추가하는 것을 우선합니다.

## Integration

| 변경 범위 | 필수 확인 |
|---|---|
| Frontend ↔ Backend | URL, method, auth, request/response field, HTTP status, `errorCode` |
| Backend ↔ AI | RabbitMQ routing, JSON schema, 상태 매핑, 중복·재시도 처리 |
| Backend ↔ S3 | object key, presigned URL 만료, 해시 검증, 임시 파일 정리 |
| DB·JPA | migration, constraint, transaction, query 수와 실행 계획 |
| 권한 | 역할 + 기관 + 부서 + 자원 소유·배정 범위 |
| 보고서 검증 | QR 발행 정보 조회와 업로드 PDF SHA-256 비교의 분리 |

## Documentation-Only Changes

```bash
git diff --check
```

- Markdown 상대 링크의 대상 파일이 존재하는지 확인합니다.
- 명령어, 경로, 상태명과 API 이름이 현재 저장소와 일치하는지 확인합니다.
- 문서가 구현되지 않은 기능을 완료된 것처럼 표현하지 않는지 확인합니다.

## Completion Checklist

- [ ] 요청 범위 밖의 파일을 수정하지 않았다.
- [ ] 관련 Source of Truth 문서를 확인하거나 갱신했다.
- [ ] 변경된 정책을 정상·실패·권한 거부 사례로 검증했다.
- [ ] 비밀값, 개인정보, 증거 원문과 대용량 산출물이 diff에 없다.
- [ ] 실행한 검증과 실행하지 못한 검증을 구분해 기록했다.
