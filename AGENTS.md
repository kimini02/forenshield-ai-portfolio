# ForenShield AI Agent Guide

이 문서는 AI 코딩 도구가 ForenShield AI를 수정할 때 가장 먼저 읽는 저장소 안내서입니다. 기존 설계 문서를 대체하지 않으며, 작업에 필요한 문서와 검증 절차로 연결하는 역할만 합니다.

## 1. Repository Map

| 경로 | 역할 | 주요 기술 |
|---|---|---|
| `frontend/` | 사용자 화면과 API 연동 | Next.js, React, TypeScript |
| `backend/` | 인증, 사건·증거, 분석 workflow, 보고서 | Java 17, Spring Boot, JPA |
| `ai/` | AI 분석 요청 스키마와 FastAPI worker | Python, FastAPI, FFmpeg |
| `docs/agent/` | AI 협업 절차, 검증 체크리스트, 기능 명세 템플릿 | Markdown |

## 2. Source of Truth

작업을 시작하기 전에 변경 범위에 맞는 문서를 읽습니다. 같은 규칙을 새 문서에 중복 작성하지 않습니다.

| 작업 범위 | 먼저 읽을 문서 |
|---|---|
| 전체 구조 | `README.md`, `backend/docs/architecture/system-overview.md` |
| Backend 구현 | `backend/docs/guides/implementation-standards.md`, `backend/docs/rule.md` |
| REST API·에러 응답 | `backend/docs/api/convention.md`, `backend/docs/api/specification.md` |
| DB·엔티티 | `backend/docs/database/erd.md` |
| RabbitMQ·S3·AI 계약 | `backend/docs/integrations/rabbitmq.md`, `backend/docs/integrations/s3.md`, `backend/docs/integrations/ai-json.md` |
| Frontend 구현 | `frontend/docs/frontend/conventions.md`, `frontend/docs/frontend/local-dev.md` |
| Frontend API 연동 | `frontend/docs/frontend-api-integration-status.md` |
| AI Server | `ai/docs/AI_SERVER_CONTEXT.md`, `ai/docs/AI_SERVER_STRUCTURE.md` |

문서와 코드가 다르면 임의로 한쪽을 정답으로 가정하지 않습니다. 차이를 확인하고, 실제 동작과 변경 목적을 근거로 둘을 함께 정리합니다.

## 3. Domain Invariants

다음 정책은 편의를 위해 우회하거나 화면에서만 처리하지 않습니다.

- 분석 취소는 `QUEUED`, `ANALYZING` 상태에서만 허용합니다. 완료·실패 등 terminal 상태의 결과는 보호합니다.
- 검토자 배정은 역할뿐 아니라 동일 기관과 동일 부서 범위를 함께 검증합니다. 화면에서 선택지를 숨기는 것만으로 권한 검증을 대신하지 않습니다.
- QR 검증은 시스템에 등록된 보고서 발행 정보 조회입니다. 사용자가 보유한 PDF의 무결성은 별도의 SHA-256 비교로 `MATCH` 또는 `MISMATCH`를 판단합니다.
- 원본 해시, 전자서명, 블록체인 앵커, CoC 이력은 서로 다른 검증 근거입니다. 하나의 성공 결과로 나머지를 대신하지 않습니다.
- 증거·분석·보고서·CoC 기록은 감사 추적성을 해치도록 임의 삭제하거나 덮어쓰지 않습니다.
- Frontend, Backend, AI 사이의 계약을 변경할 때 한 구성 요소만 수정한 채 완료로 판단하지 않습니다.
- AI 분석 점수는 수사·법적 판단을 대신하지 않습니다. 실제 결과가 없을 때 mock 값이나 추정값을 만들어 표시하지 않습니다.

## 4. Working Rules

1. `git status`와 변경 대상 파일을 확인하고 사용자의 기존 변경을 보존합니다.
2. 위 표에서 관련 문서를 읽고 현재 계약과 상태 흐름을 확인합니다.
3. API, DB, 권한, 상태 전이 또는 둘 이상의 서비스를 바꾸는 작업은 구현 전에 기능 명세를 작성하거나 갱신합니다.
4. 요청을 충족하는 가장 작은 범위로 구현하고, 무관한 리팩터링을 섞지 않습니다.
5. 변경 범위에 맞는 테스트와 정적 검사를 실행합니다.
6. 계약이나 실행 방법이 달라졌다면 관련 문서를 같은 변경에 포함합니다.
7. 완료 보고에는 변경 내용, 실행한 검증, 실행하지 못한 검증과 남은 위험을 구분해 적습니다.

세부 절차는 `docs/agent/development-workflow.md`, 명령은 `docs/agent/verification.md`를 따릅니다.

## 5. Security and Data Handling

- `.env`, JWT secret, AWS credential, 운영용 private key와 certificate 원문을 커밋하거나 출력하지 않습니다.
- presigned URL, access token, 개인정보, 증거 파일명과 메타데이터를 로그·예제·오류 메시지에 불필요하게 노출하지 않습니다.
- 실제 증거 영상, 대용량 모델 파일, 분석 산출물을 Git에 추가하지 않습니다.
- 외부 입력, 업로드 파일, AI 응답을 신뢰하지 않고 각 경계에서 검증합니다.
- 파괴적인 Git 명령과 강제 push를 사용하지 않습니다.

## 6. AI Collaboration Principle

AI는 요구사항 정리, 대안 비교, 구현 보조, 테스트 케이스 도출과 문서화에 사용합니다. 최종 설계 결정, 권한·보안 정책, 코드 diff 검토, 테스트 결과 확인은 사람이 책임집니다. 실행하지 않은 테스트나 확인하지 않은 동작을 완료했다고 기록하지 않습니다.
