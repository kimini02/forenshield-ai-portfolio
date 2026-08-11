# AI-Assisted Development Workflow

## 1. Before Coding

1. 사용자 요청을 한 문장으로 정리하고 완료 조건을 적습니다.
2. `git status`와 최근 diff를 확인해 기존 작업과 충돌하는지 살핍니다.
3. `AGENTS.md`의 Source of Truth 표에서 관련 문서를 읽습니다.
4. 변경되는 구성 요소와 서비스 간 계약을 나열합니다.
5. 아래 조건에 해당하면 `feature-spec-template.md`로 짧은 명세를 먼저 작성합니다.

명세를 저장해야 한다면 템플릿을 복사해 변경과 가장 가까운 `docs/` 아래에 `{feature-name}-spec.md`로 두고, 관련 원본 문서에서 연결합니다.

### Spec First가 필요한 변경

- Frontend, Backend, AI 중 둘 이상에 영향을 주는 변경
- REST API, RabbitMQ 메시지 또는 AI JSON 계약 변경
- DB schema, migration, 조회 성능에 영향을 주는 변경
- 인증, 역할, 기관·부서 접근 범위 변경
- 분석 상태 전이, 취소, 재시도, timeout 정책 변경
- 증거 해시, 전자서명, 블록체인, CoC, PDF 검증 흐름 변경

오탈자, 문구, 국소적인 스타일 수정처럼 계약에 영향이 없는 변경은 별도 명세 없이 진행할 수 있습니다.

## 2. During Implementation

- 한 변경에는 한 목적만 담습니다.
- 기존 계층과 도메인 경계를 따르고 우회 경로를 추가하지 않습니다.
- 외부 계약이 바뀌면 producer와 consumer를 함께 확인합니다.
- 실패, 빈 결과, 권한 없음, 재시도와 중복 요청을 정상 흐름만큼 명시적으로 처리합니다.
- 정책을 코드로 강제하고, UI의 버튼 숨김이나 안내 문구만으로 보안을 대신하지 않습니다.
- mock과 실제 데이터를 섞지 않으며 real mode에서 가짜 분석 근거를 만들지 않습니다.
- 수정한 비즈니스 규칙에는 회귀를 막는 테스트를 추가합니다.

## 3. Cross-Service Contract Change

서비스 간 계약을 바꿀 때는 다음 순서로 확인합니다.

1. 요청·응답 또는 메시지 schema와 호환성 범위를 정의합니다.
2. Backend API 문서와 AI integration 문서 등 원본 계약을 갱신합니다.
3. producer와 consumer 양쪽의 타입·DTO·validation을 수정합니다.
4. 기존 데이터나 이전 consumer가 있다면 하위 호환 또는 migration 전략을 적습니다.
5. 단위 테스트 후 실제 경계에서 최소 한 번 통합 검증합니다.

## 4. Review and Handoff

구현이 끝나면 `verification.md`에서 변경 경로에 맞는 검증을 실행합니다. 최종 보고는 다음 네 항목을 포함합니다.

1. 변경한 내용
2. 중요한 설계 결정과 이유
3. 실행한 검증과 결과
4. 실행하지 못한 검증 또는 남은 위험

AI가 작성한 코드라는 이유로 검토 범위를 줄이지 않습니다. 특히 권한 조건, 상태 전이, 트랜잭션 경계, 개인정보·비밀값 노출, 파일 무결성 처리는 사람이 직접 확인합니다.
