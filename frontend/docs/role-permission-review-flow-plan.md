# ForenShield AI 권한 및 검토자 배정 플로우 계획

## 목적

ForenShield AI는 경찰기관, 수사기관, 포렌식 기관 등 여러 기관에서 사용하는 딥페이크 포렌식 분석 시스템이다.

v2 프론트엔드는 사용자 역할과 사건별 접근 권한을 분리해서 관리한다.

- 사용자 역할: 사용자가 어떤 기능을 사용할 수 있는가
- 사건별 접근 권한: 사용자가 어떤 사건을 볼 수 있는가

특히 검토자는 전체 사건을 볼 수 없다. 검토자는 기관 관리자가 본인에게 배정한 사건만 조회할 수 있어야 한다.

## 역할 구조

프론트에서 사용할 역할은 3개로 정리한다.

```ts
type UserRole = "ORG_ADMIN" | "INVESTIGATOR" | "REVIEWER";
```

### ORG_ADMIN

기관 관리자.

가능한 기능:

- 자기 기관 전체 사건 조회
- 사용자 승인
- 사용자 역할 변경
- 검토 요청 사건 확인
- 사건별 검토자 배정
- 보고서 승인 상태 확인
- 관리자 페이지 접근
- 사건 삭제

### INVESTIGATOR

담당자 또는 수사관.

가능한 기능:

- 사건 등록
- 증거 업로드
- AI 분석 요청
- 분석 결과 조회
- 보고서 생성
- 검토 요청

불가능한 기능:

- 검토자 직접 배정
- 사용자 권한 변경
- 모든 사건 조회
- 보고서 최종 승인
- 사건 삭제

### REVIEWER

검토자.

가능한 기능:

- 본인에게 배정된 사건만 조회
- 분석 결과 확인
- 딥페이크 상세 분석 확인
- 위변조/무결성 검증 확인
- 보고서 검토
- 보고서 승인

불가능한 기능:

- 사건 등록
- 증거 업로드
- AI 분석 요청
- 검토 요청
- 검토자 배정
- 전체 사건 조회
- 사용자 권한 변경
- 사건 삭제

## 우상단 사용자 표시

현재 우상단의 테스트 사용자 표시는 실제 로그인 사용자 정보처럼 보이도록 수정한다.

기본 표시:

```text
김민희 · 담당자
```

드롭다운:

```text
김민희
서울경찰청
사이버수사팀
담당자

내 정보
로그아웃
```

역할 표시명:

```ts
const roleLabelMap = {
  ORG_ADMIN: "기관 관리자",
  INVESTIGATOR: "담당자",
  REVIEWER: "검토자",
};
```

## Mock 사용자 데이터

백엔드 연동 전까지 mock 데이터로 권한 처리가 가능하도록 사용자 구조를 맞춘다.

```ts
type MockUser = {
  id: string;
  name: string;
  organizationId: string;
  organizationName: string;
  department: string;
  role: UserRole;
};
```

담당자 예시:

```ts
const investigatorUser = {
  id: "user-001",
  name: "김민희",
  organizationId: "org-police-seoul",
  organizationName: "서울경찰청",
  department: "사이버수사팀",
  role: "INVESTIGATOR",
};
```

검토자 예시:

```ts
const reviewerUser = {
  id: "user-002",
  name: "박검토",
  organizationId: "org-police-seoul",
  organizationName: "서울경찰청",
  department: "디지털포렌식팀",
  role: "REVIEWER",
};
```

기관 관리자 예시:

```ts
const adminUser = {
  id: "user-003",
  name: "이관리",
  organizationId: "org-police-seoul",
  organizationName: "서울경찰청",
  department: "관리자실",
  role: "ORG_ADMIN",
};
```

## 사건 데이터 확장

사건 데이터에는 검토 상태와 검토자 정보를 추가한다.

```ts
type ReviewStatus =
  | "NONE"
  | "REVIEW_REQUESTED"
  | "REVIEW_ASSIGNED"
  | "REVIEW_COMPLETED"
  | "REPORT_APPROVED";
```

```ts
type CaseItem = {
  id: string;
  caseName: string;
  organizationId: string;
  department: string;
  createdBy: string;
  assigneeId: string;
  reviewerId: string | null;
  reviewStatus: ReviewStatus;
  status: "REGISTERED" | "ANALYZING" | "ANALYSIS_COMPLETED" | "FAILED";
  aiResult: "낮음" | "검토 필요" | "위험";
  riskScore: number;
  reviewRequestedAt: string | null;
};
```

검토 상태 표시:

```ts
const reviewStatusLabelMap = {
  NONE: "검토 없음",
  REVIEW_REQUESTED: "검토 요청됨",
  REVIEW_ASSIGNED: "검토 중",
  REVIEW_COMPLETED: "검토 완료",
  REPORT_APPROVED: "보고서 승인 완료",
};
```

상태 색상:

- 검토 없음: 회색
- 검토 요청됨: 파랑
- 검토 중: 주황
- 검토 완료: 초록
- 보고서 승인 완료: 초록

## 권한 함수 분리

`lib/permissions.ts`를 만들고 역할 권한과 사건 접근 권한을 한 곳에서 관리한다.

```ts
export function isOrgAdmin(user: MockUser) {
  return user.role === "ORG_ADMIN";
}

export function isInvestigator(user: MockUser) {
  return user.role === "INVESTIGATOR";
}

export function isReviewer(user: MockUser) {
  return user.role === "REVIEWER";
}

export function isSameOrganization(user: MockUser, caseItem: CaseItem) {
  return user.organizationId === caseItem.organizationId;
}

export function isCaseOwner(user: MockUser, caseItem: CaseItem) {
  return caseItem.createdBy === user.id || caseItem.assigneeId === user.id;
}

export function isAssignedReviewer(user: MockUser, caseItem: CaseItem) {
  return caseItem.reviewerId === user.id;
}

export function canViewCase(user: MockUser, caseItem: CaseItem) {
  if (!isSameOrganization(user, caseItem)) return false;
  if (isOrgAdmin(user)) return true;
  if (isCaseOwner(user, caseItem)) return true;
  if (isAssignedReviewer(user, caseItem)) return true;
  return false;
}

export function canCreateCase(user: MockUser) {
  return isOrgAdmin(user) || isInvestigator(user);
}

export function canUploadEvidence(user: MockUser, caseItem: CaseItem) {
  if (!canViewCase(user, caseItem)) return false;
  return isOrgAdmin(user) || isCaseOwner(user, caseItem);
}

export function canRequestAnalysis(user: MockUser, caseItem: CaseItem) {
  if (!canViewCase(user, caseItem)) return false;
  return isOrgAdmin(user) || isCaseOwner(user, caseItem);
}

export function canRequestReview(user: MockUser, caseItem: CaseItem) {
  if (!canViewCase(user, caseItem)) return false;
  return (
    (isOrgAdmin(user) || isCaseOwner(user, caseItem)) &&
    caseItem.status === "ANALYSIS_COMPLETED" &&
    caseItem.reviewStatus === "NONE"
  );
}

export function canAssignReviewer(user: MockUser, caseItem: CaseItem) {
  if (!canViewCase(user, caseItem)) return false;
  return isOrgAdmin(user) && caseItem.reviewStatus === "REVIEW_REQUESTED";
}

export function canApproveReport(user: MockUser, caseItem: CaseItem) {
  if (!canViewCase(user, caseItem)) return false;
  return isOrgAdmin(user) || isAssignedReviewer(user, caseItem);
}

export function canDeleteCase(user: MockUser, caseItem: CaseItem) {
  if (!canViewCase(user, caseItem)) return false;
  return isOrgAdmin(user);
}
```

## 사건 목록 필터링

사건 목록은 권한 함수 기준으로 필터링한다.

```ts
function getVisibleCases(user: MockUser, cases: CaseItem[]) {
  return cases.filter((caseItem) => canViewCase(user, caseItem));
}
```

표시 기준:

- ORG_ADMIN: 자기 기관 전체 사건
- INVESTIGATOR: 본인이 등록했거나 담당자인 사건
- REVIEWER: 본인에게 검토자로 배정된 사건

REVIEWER에게는 사건 등록 버튼을 보여주지 않는다.

## 사건 상세 검토 요청 흐름

담당자/수사관은 검토자를 직접 선택하지 않는다.

담당자는 분석 완료 후 사건 상세에서 `검토 요청`만 할 수 있다. 검토자 배정은 기관 관리자가 관리자 페이지에서 처리한다.

버튼 노출 조건:

- 현재 사용자가 ORG_ADMIN이거나 사건 담당자
- 사건 상태가 `ANALYSIS_COMPLETED`
- `reviewStatus`가 `NONE`

버튼:

```text
[검토 요청]
```

모달:

```text
검토 요청

이 사건의 분석 결과를 검토자에게 전달합니다.
검토자 배정은 기관 관리자가 관리자 페이지에서 진행합니다.

요청 메모
[선택 입력]

[취소] [검토 요청]
```

검토 요청 완료 시:

```ts
caseItem.reviewStatus = "REVIEW_REQUESTED";
caseItem.reviewRequestedAt = new Date().toISOString();
```

화면 표시:

```text
검토 요청됨
관리자 배정 대기
```

## 관리자 페이지 구성

관리자 페이지는 기관 관리자만 접근할 수 있다.

구성은 두 영역으로 나눈다.

### 사용자 관리

회원가입한 사용자를 승인하고 역할을 부여하는 영역이다.

표 컬럼:

```text
이름 | 기관 | 소속 | 상태 | 역할 | 관리
```

예시:

```text
김민희 | 서울경찰청 | 사이버수사팀 | 승인 대기 | [담당자 ▼] | [승인]
박검토 | 서울경찰청 | 디지털포렌식팀 | 활성 | 검토자 | [역할 변경]
이관리 | 서울경찰청 | 관리자실 | 활성 | 기관 관리자 | [역할 변경]
```

역할 선택값:

```text
기관 관리자
담당자
검토자
```

### 검토 요청 사건 관리

담당자가 검토 요청한 사건을 관리자가 확인하고 검토자를 배정하는 영역이다.

표 제목:

```text
검토 요청 사건
```

표 컬럼:

```text
사건명 | 담당자 | AI 결과 | 검토 요청일 | 검토자 | 상태 | 관리
```

주의:

- 컬럼명은 `위험도`가 아니라 `AI 결과`를 사용한다.
- AI 결과 값은 `낮음`, `검토 필요`, `위험` 중 하나로 표시한다.
- 검토자 선택 드롭다운에는 같은 기관의 `role === "REVIEWER"` 사용자만 표시한다.
- 배정 버튼 클릭 시 사건의 `reviewerId`를 선택한 검토자 ID로 설정한다.
- 배정 후 `reviewStatus`를 `REVIEW_ASSIGNED`로 변경한다.
- 배정된 사건은 해당 검토자의 사건 목록에 나타난다.

## 검토자 사건 목록

REVIEWER로 로그인한 사용자는 본인에게 배정된 사건만 볼 수 있다.

조건:

```ts
caseItem.reviewerId === currentUser.id
```

검토자 사건 카드 예시:

```text
SNS 유포 영상 딥페이크 의심 사건
담당자: 김민희
AI 결과: 위험
상태: 검토 중

[결과 확인]
```

검토자에게 보이는 기능:

- 결과 확인
- 딥페이크 상세 분석 보기
- 위변조/무결성 검증 보기
- 보고서 검토
- 보고서 승인

검토자에게 숨길 기능:

- 사건 등록
- 증거 추가
- AI 분석 요청
- 검토 요청
- 검토자 배정
- 사건 삭제

## 보고서 승인 흐름

검토자가 보고서를 승인하면 사건의 `reviewStatus`를 `REPORT_APPROVED`로 변경한다.

```ts
caseItem.reviewStatus = "REPORT_APPROVED";
```

화면 표시:

```text
보고서 승인 완료
```

보고서 승인 버튼은 아래 조건으로 표시한다.

```ts
canApproveReport(currentUser, caseItem)
```

## 역할별 화면 버튼

### ORG_ADMIN

보이는 버튼:

- 증거 추가
- AI 분석 요청
- 결과보기
- 검토자 배정 상태 확인
- 보고서 생성
- 보고서 승인
- 사건 삭제

### INVESTIGATOR

보이는 버튼:

- 증거 추가
- AI 분석 요청
- 결과보기
- 검토 요청
- 보고서 생성

숨김:

- 검토자 직접 배정
- 보고서 승인
- 사건 삭제

### REVIEWER

보이는 버튼:

- 결과보기
- 딥페이크 상세 분석 보기
- 위변조/무결성 검증 보기
- 보고서 검토
- 보고서 승인

숨김:

- 사건 등록
- 증거 추가
- AI 분석 요청
- 검토 요청
- 검토자 배정
- 사건 삭제

## UX 문구

관리자 페이지에서는 `위험도` 대신 `AI 결과`를 사용한다.

이유:

- 위험도는 권한이나 중요도처럼 오해될 수 있다.
- AI 결과는 분석 결과 등급이라는 의미가 더 명확하다.

표시 예시:

```text
AI 결과: 위험
AI 결과: 검토 필요
AI 결과: 낮음
```

검토 요청 상태:

```text
검토 요청됨
관리자 배정 대기
```

검토자 배정 완료:

```text
검토자가 배정되었습니다.
```

검토자 화면 상태:

```text
검토 중
보고서 승인 필요
```

## 최종 사용자 흐름

```text
1. 회원가입
2. 관리자 페이지에서 사용자 승인 및 역할 부여
3. 담당자가 사건 등록
4. 담당자가 증거 업로드
5. 담당자가 AI 분석 요청
6. AI 분석 완료
7. 담당자가 사건 상세에서 검토 요청
8. 관리자 페이지의 검토 요청 사건 목록에 표시
9. 관리자가 검토자를 배정
10. 검토자는 배정된 사건만 사건 목록에서 확인
11. 검토자가 결과와 보고서를 검토
12. 검토자가 보고서 승인
```

## 구현 순서

1. `lib/permissions.ts` 생성
2. mock 사용자 데이터와 사건 데이터 구조 확장
3. 우상단 사용자 표시 수정
4. 사건 목록 권한 필터링 적용
5. 사건 상세 버튼 노출 조건 적용
6. 검토 요청 모달 추가
7. 관리자 페이지에 사용자 관리/검토 요청 사건 관리 추가
8. 검토자 로그인 시 배정 사건만 보이도록 처리
9. 보고서 승인 상태 연결

## 검증 항목

- REVIEWER는 사건 등록 버튼을 볼 수 없다.
- REVIEWER는 본인에게 배정되지 않은 사건을 볼 수 없다.
- INVESTIGATOR는 검토자를 직접 배정할 수 없다.
- INVESTIGATOR는 분석 완료 사건에서 검토 요청을 할 수 있다.
- ORG_ADMIN은 검토 요청 사건에 검토자를 배정할 수 있다.
- 검토자 배정 후 해당 REVIEWER의 사건 목록에 사건이 표시된다.
- REVIEWER는 결과 확인과 보고서 승인만 수행할 수 있다.
- `AI 결과` 문구가 관리자 페이지에 일관되게 표시된다.
- `corepack pnpm exec tsc --noEmit`이 통과한다.
