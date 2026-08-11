# AI Collaboration Guide

ForenShield AI의 AI 협업 문서는 기존 개발 문서를 다시 쓰지 않고, AI와 사람이 같은 기준으로 작업 범위와 완료 조건을 확인하기 위한 최소한의 운영 체계입니다.

## Documents

| 문서 | 용도 |
|---|---|
| [`../../AGENTS.md`](../../AGENTS.md) | 저장소 구조, 핵심 정책, 작업별 원본 문서 안내 |
| [`development-workflow.md`](./development-workflow.md) | 작업 시작부터 검토까지의 공통 절차 |
| [`verification.md`](./verification.md) | 구성 요소별 검증 명령과 완료 보고 기준 |
| [`feature-spec-template.md`](./feature-spec-template.md) | 복잡한 기능 변경 전에 사용하는 간단한 명세 템플릿 |

## How We Use AI

AI가 보조하는 영역은 다음과 같습니다.

- 요구사항을 구현 가능한 단위로 나누기
- 설계 대안과 trade-off 정리
- 반복 코드와 테스트 초안 작성
- 변경 영향 범위와 누락된 예외 상황 탐색
- 코드와 함께 유지되는 기술 문서 작성

AI 결과를 그대로 채택하지 않습니다. 담당자가 코드 diff, 도메인 정책, API 계약, 보안 영향과 검증 결과를 확인한 뒤 반영합니다.

## Documentation Rule

- 기존 API·ERD·구현 규칙은 각 구성 요소의 `docs/`가 원본입니다.
- `AGENTS.md`는 원본 문서로 가는 지도이며 세부 명세를 복제하지 않습니다.
- API, 상태 전이, 권한, DB 또는 서비스 간 메시지 계약을 바꾸면 관련 원본 문서도 함께 갱신합니다.
- 반복되는 작업 방식이 실제로 자리 잡기 전에는 자동 hook이나 전용 skill을 추가하지 않습니다.
