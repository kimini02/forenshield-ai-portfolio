# 프론트엔드 공통 리팩토링 가이드

이 문서는 팀원별로 나뉘어 작성된 프론트엔드 코드를 같은 기준으로 정리하기 위한 공통 규칙입니다.

목표는 모든 화면을 한 번에 갈아엎는 것이 아니라, 앞으로 개발하는 코드와 리팩토링하는 코드를 같은 구조와 스타일로 맞추는 것입니다.

## 1. 기본 원칙

- 새 기능 개발 전에 이 문서의 규칙을 먼저 확인합니다.
- 기존 코드는 기능을 망가뜨리지 않는 범위에서 페이지 단위로 천천히 맞춥니다.
- 공통 컴포넌트, API 함수, 타입은 각 페이지 안에 중복으로 만들지 않습니다.
- API가 완성되지 않은 기능은 실제 API와 같은 함수명/응답 타입을 가진 mock으로 먼저 연결합니다.
- 리팩토링은 디자인 변경이 아니라 구조 통일 작업입니다. 화면이 크게 달라져야 하는 경우 별도 작업으로 분리합니다.

## 2. 권장 폴더 구조

```text
app/
  <route>/
    page.tsx
    _components/
    _types/
    _lib/

components/
  ui/
  site-header.tsx
  <shared-feature>.tsx

lib/
  api/
  mock-forensic-api.ts
  evidence-api.ts
  auth.ts
  utils.ts

docs/
```

### app 라우트 내부

- `page.tsx`는 라우트 진입점 역할만 합니다.
- 화면 안에서만 쓰는 컴포넌트는 `app/<route>/_components`에 둡니다.
- 해당 화면에서만 쓰는 타입은 `app/<route>/_types`에 둡니다.
- 해당 화면에서만 쓰는 정렬, 포맷팅, 변환 함수는 `app/<route>/_lib`에 둡니다.

### components

- 여러 페이지에서 공유하는 컴포넌트만 둡니다.
- `components/ui`는 버튼, 카드, 탭, 배지 같은 순수 UI 컴포넌트만 둡니다.
- 도메인 로직이 강한 컴포넌트는 `components/ui`에 넣지 않습니다.

### lib

- API 호출, mock API, 인증, 저장소, 공통 유틸을 둡니다.
- 페이지 UI를 직접 import하지 않습니다.
- 서버 응답 타입과 mock 응답 타입은 최대한 같은 형태를 유지합니다.

## 3. 컴포넌트 작성 규칙

컴포넌트는 아래 순서로 분리합니다.

```text
Page
  화면 전체 레이아웃과 데이터 로딩 조립

Section
  화면의 큰 영역

Feature Component
  업로드 패널, 최근 분석 목록, 메타데이터 카드 등 기능 단위

UI Component
  버튼, 카드, 탭, 배지, 입력창 등 표현 단위
```

권장:

- 컴포넌트 파일명은 kebab-case를 사용합니다.
  - 예: `upload-panel.tsx`, `recent-analyses.tsx`
- 컴포넌트 이름은 PascalCase를 사용합니다.
  - 예: `UploadPanel`, `RecentAnalyses`
- props 타입은 컴포넌트 파일 안에 `type ComponentNameProps`로 둡니다.
- 한 파일이 너무 길어지면 먼저 내부 섹션 컴포넌트로 나눕니다.

피하기:

- `page.tsx`에 모든 UI와 로직을 몰아넣기
- 페이지마다 같은 카드/버튼 스타일을 새로 만들기
- API 호출 코드를 컴포넌트 안에 직접 여러 번 작성하기
- 화면과 관련 없는 타입을 페이지 폴더 안에 숨기기

## 4. API와 mock 규칙

API 함수는 실제 백엔드 연동 전에도 최종 함수명을 먼저 정합니다.

예:

```ts
fetchEvidenceStats()
uploadEvidence(file, caseName)
startEvidenceAnalysis(evidenceIds, caseName)
fetchEvidenceDetail(evidenceId)
fetchMyAnalysisHistory(options)
```

mock은 다음 조건을 지킵니다.

- 실제 API 함수와 같은 입력/출력 타입을 사용합니다.
- 컴포넌트는 mock 여부를 몰라도 됩니다.
- mock 전환은 API 파일에서만 처리합니다.
- mock 데이터는 localStorage를 사용해 새로고침 후에도 흐름을 확인할 수 있게 합니다.

현재 기준:

```ts
const USE_MOCK_API = process.env.NEXT_PUBLIC_USE_MOCK_API !== "false"
```

- 기본값은 mock 사용입니다.
- 실제 API를 붙일 때는 `NEXT_PUBLIC_USE_MOCK_API=false`로 전환합니다.

## 5. 상태 UI 규칙

모든 데이터 화면은 아래 상태를 명확히 나눕니다.

```text
loading
  데이터를 불러오는 중

error
  요청 실패 또는 권한 문제

empty
  요청은 성공했지만 표시할 데이터 없음

success
  정상 데이터 표시
```

권장 문구:

- loading: `불러오는 중...`, `분석 데이터를 수신 중입니다...`
- error: `데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.`
- empty: `아직 분석을 시작한 증거가 없습니다.`

버튼 상태:

- 필수 입력값이 없으면 버튼을 비활성화합니다.
- 비활성화 사유가 사용자에게 필요하면 `title` 또는 가까운 설명 문구로 알려줍니다.
- 서버 요청 중에는 중복 클릭을 막습니다.

## 6. 디자인 통일 규칙

기본 스타일은 Tailwind v4와 `components/ui`를 사용합니다.

공통 기준:

- 카드: `rounded-xl border border-border bg-card`
- 섹션 간격: `space-y-6`, 큰 영역은 `gap-8`
- 아이콘 크기: 보통 `size-4`, 강조 아이콘은 `size-5`
- 본문 설명: `text-sm text-muted-foreground`
- 보조 정보: `text-xs text-muted-foreground`
- 해시/ID: `font-mono text-xs` 또는 `font-mono text-[10px]`

페이지별로 색상과 radius를 임의로 새로 만들지 않습니다.

## 7. 타입 규칙

도메인 타입은 중복 정의하지 않는 것을 우선합니다.

권장 위치:

```text
lib/evidence-api.ts
  UploadResult
  EvidenceStatsResponse
  MediaMetadata
  AnalysisStatusResponse

lib/api/evidence-detail.ts
  EvidenceDetailData
  CaseDetailData
  ModuleResult

app/<route>/_types
  해당 라우트에서만 쓰는 화면 타입
```

공통 상태 문자열은 union type으로 고정합니다.

```ts
type AnalysisStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED"
```

## 8. 리팩토링 순서

리팩토링은 아래 순서로 진행합니다.

1. 공통 문서와 타입 기준 확인
2. 깨지는 화면, 런타임 오류, 타입 오류 먼저 정리
3. API/mock 레이어 통일
4. 공통 UI 컴포넌트 정리
5. 메인 페이지 리팩토링
6. 마이페이지 리팩토링
7. 사건 상세 / 증거 상세 리팩토링
8. 관리자 페이지 리팩토링
9. 전체 타입체크와 브라우저 화면 검증

## 9. 작업 전 체크리스트

- 이 코드가 특정 페이지에서만 쓰이는가, 여러 페이지에서 쓰이는가?
- 같은 API 호출 또는 타입이 이미 있는가?
- loading/error/empty/success 상태가 모두 있는가?
- mock에서 실제 API로 바꿔도 컴포넌트 코드를 수정하지 않아도 되는가?
- 화면 스타일이 기존 카드, 버튼, 배지 기준과 맞는가?
- 다른 팀원이 맡은 라우트나 공통 파일을 건드리는가?

## 10. 현재 정리 대상

현재 우선 정리할 부분:

- `components/ui/button.tsx` 사용 방식 통일
- `components/ui/toast.tsx`, `components/ui/use-toast.ts` 타입 오류 정리
- `lib/api-client.ts`의 `HeadersInit` 타입 처리 정리
- `components/upload-panel.tsx`의 메타데이터 타입 추론 오류 정리
- admin 페이지의 `asChild` 사용 방식 정리
- mock API와 실제 API 전환 방식 유지

위 항목은 기능 개발 전 또는 병행 작업 초기에 먼저 안정화하는 것을 권장합니다.
