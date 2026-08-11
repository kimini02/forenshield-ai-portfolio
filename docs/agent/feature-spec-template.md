# Feature Spec: {기능명}

> 복잡한 변경을 시작하기 전에 필요한 항목만 짧게 작성합니다. 해당 없는 항목은 삭제하거나 `N/A`로 표시합니다.

## 1. Overview

- 상태: Draft / Review / Approved / Done
- 작성자:
- 작성일:
- 관련 이슈·문서:

### Problem

어떤 사용자 또는 시스템 문제가 있는가?

### Goal

이 변경으로 무엇이 가능해져야 하는가?

### Non-goals

이번 변경에서 의도적으로 다루지 않는 것은 무엇인가?

## 2. Scope

- Frontend:
- Backend:
- AI Server:
- Data / Infrastructure:

## 3. User and System Flow

1. 사용자가 수행하는 행동
2. 시스템이 검증하는 조건
3. 성공 시 저장·반환하는 결과
4. 실패 시 상태와 사용자 응답

## 4. Contract

### API or Message

- Method / Path 또는 Queue:
- Authentication / Authorization:
- Request:
- Success Response:
- Error Response:
- Compatibility:

### State Transition

```text
CURRENT_STATE → NEXT_STATE
```

- 허용 조건:
- 금지 조건:
- 재시도·중복 요청 정책:

## 5. Data and Security

- Entity / Column / Index / Migration:
- Transaction boundary:
- 역할·기관·부서·자원 범위:
- 개인정보·비밀값·증거 데이터 처리:
- SHA-256 / 전자서명 / 블록체인 / CoC 영향:

## 6. Failure and Operations

- 예상 실패:
- Timeout / Retry / Idempotency:
- 필요한 로그·metric·alert:
- Rollback 또는 비활성화 방법:

## 7. Verification

- [ ] 정상 흐름
- [ ] validation 실패
- [ ] 인증·권한 거부
- [ ] 상태 전이와 terminal 상태 보호
- [ ] 중복·재시도·timeout
- [ ] 서비스 간 계약
- [ ] 관련 문서 갱신

## 8. Decisions and Trade-offs

선택한 방식, 고려한 대안, 선택 이유를 간단히 기록합니다.

## 9. Definition of Done

- [ ] 구현 완료
- [ ] 변경 범위에 맞는 자동 테스트 통과
- [ ] 필요한 통합·수동 검증 완료
- [ ] 문서와 실제 동작 일치
- [ ] 남은 위험과 후속 작업 기록
