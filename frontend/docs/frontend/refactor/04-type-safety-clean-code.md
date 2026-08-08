# 리팩토링 4 - 타입 안정성 / 클린코드 정리

> 순서: package manager 정리 완료 -> **이 문서(우선 처리)** -> 01/02/03 단계 병행 가능
> 대상 repo: `/Users/kimmini/sk-final-deepfake/frontend-forensic`

## 목표

현재 `pnpm build`는 통과하지만, `next.config.mjs`에서 타입 에러를 무시하고 있어 실제 TypeScript 오류가 숨겨져 있다.
먼저 `pnpm exec tsc --noEmit`을 통과시키고, 이후 `ignoreBuildErrors`를 제거해 빌드가 타입 안정성을 보장하도록 만든다.

## 진행 결과

- 완료: `metadata-report-tab.tsx`, `analysis-request-flow.tsx`, `upload-panel.tsx`, toast 계열 타입 에러 수정.
- 완료: `next.config.mjs`의 `ignoreBuildErrors` 제거.
- 완료: `corepack pnpm exec tsc --noEmit --pretty false` 통과.
- 완료: `corepack pnpm build` 통과. 단, Google Fonts 다운로드 때문에 네트워크 허용 환경에서 검증했다.
- 완료: ESLint 9 + `eslint-config-next` 기반 `eslint.config.mjs` 추가.
- 완료: `corepack pnpm lint` 통과.
- 남음: lint warning 29개. 대부분 기존 `useEffect` 내부 setState 패턴, `<img>` 사용, unused import/variable이다.

## 현재 확인된 문제

### A. 빌드가 TypeScript 에러를 숨김

파일:
- `next.config.mjs`

문제:
```js
typescript: {
  ignoreBuildErrors: true,
}
```

처리:
- 아래 TypeScript 오류를 먼저 해결한다.
- `pnpm exec tsc --noEmit --pretty false` 통과 확인.
- 마지막에 `ignoreBuildErrors: true` 제거.
- `pnpm build` 통과 확인.

### B. 현재 TypeScript 에러

1. `app/cases/[id]/_components/metadata-report-tab.tsx`
   - `evidenceInfo.fileSizeText`는 `EvidenceInfo`에 없는 필드.
   - `fileSize` + `formatFileSize` 조합으로 표시하거나 타입에 실제 필드를 추가할지 결정.

2. `app/main/_components/analysis-request-flow.tsx`
   - `buildCaseDetailHref` 정의/import 없음.
   - 기존 공통 함수 `buildCaseDetailPath` 또는 현재 라우팅 정책에 맞는 helper로 교체.

3. `components/ui/toast.tsx`, `components/ui/use-toast.ts`
   - `@base-ui/react/toast` import/type 사용이 현재 패키지 타입과 맞지 않음.
   - toast primitive API를 현재 버전에 맞추거나, 프로젝트 내부 toast 타입을 단순화.
   - `app/mypage/edit/page.tsx`의 toast 호출 타입 오류도 함께 해결.

4. `components/upload-panel.tsx`
   - `buildMetadataItems()`의 `flatMap` 반환 union 타입 추론 실패.
   - `MetadataDisplayItem[]` 반환을 명시하거나 `items: MetadataDisplayItem[]`에 push하는 방식으로 변경.

## C. lint 복구

파일:
- `package.json`

문제:
```json
"lint": "eslint ."
```

현재 `eslint` dependency가 없어 `pnpm lint`가 실패한다.

처리:
- Next 16 기준 ESLint 설정을 추가한다.
- 필요한 devDependencies를 추가한다.
- `pnpm lint` 통과 확인.

## D. 공통 포맷터 중복 제거

공통 파일:
- `lib/formatters.ts`

중복 후보:
- `components/upload-panel/upload-file-card.tsx` - `formatBytes`
- `components/metadata-info.tsx` - `formatBytes`, `formatUploadedAt`
- `components/recent-analyses.tsx` - `formatUploadedAt`
- `app/main/_components/dashboard-overview.tsx` - `formatDashboardDate`
- `app/admin/evidences/page.tsx` - `formatFileSize`
- `app/admin/evidences/[id]/page.tsx` - `formatFileSize`
- `app/compare/_components/compare-verification-flow.tsx` - `formatDateTimeLabel`, `formatFileSize`

처리:
- 날짜/시간은 `formatDateTime` 또는 신규 `formatDate`로 통일.
- 파일 크기는 `formatFileSize`로 통일.
- 화면별 zero label, min/max unit 차이가 있으면 옵션만 유지한다.

## E. 상태/라벨 중복 제거

공통 파일:
- `lib/status-labels.ts`

중복 후보:
- `app/cases/[id]/page.tsx` - `normalizeStatus`, `getStatusLabel`, `getCaseStatusLabel`
- `app/compare/_components/compare-verification-flow.tsx` - `getCaseStatusLabel`
- `app/main/_components/dashboard-overview.tsx` - `normalizeDashboardStatus`

처리:
- `normalizeAnalysisStatus()` 같은 공통 helper를 추가한다.
- 상태 표시 문자열은 `getAnalysisStatusLabel()`을 우선 사용한다.
- 화면별 특별 문구가 필요한 경우만 wrapper를 둔다.

## F. 큰 파일 분리 우선순위

현재 큰 파일:
```text
lib/mock/forensic-api.ts                         929 lines
lib/api/admin.ts                                 813 lines
components/upload-panel.tsx                      633 lines
app/cases/[id]/_components/metadata-report-tab.tsx 613 lines
app/main/_components/dashboard-overview.tsx      598 lines
app/cases/[id]/_components/deepfake-v2-tab.tsx   578 lines
```

권장 순서:
1. `components/upload-panel.tsx`
   - 이미 TypeScript 에러가 있어 수정 효과가 큼.
   - metadata item builder / analysis control helper / UI component 분리.
2. `lib/api/admin.ts`
   - API 호출, mock 분기, mapper, dashboard stats가 한 파일에 섞임.
   - `admin/users`, `admin/logs`, `admin/invite-codes`, `admin/evidences`, `admin/stats`로 분리.
3. `lib/mock/forensic-api.ts`
   - mock storage, sample data, mock API 함수 분리.
4. `metadata-report-tab.tsx`, `dashboard-overview.tsx`, `deepfake-v2-tab.tsx`
   - pure display component와 data builder를 먼저 분리.

## G. mock / real 경계 정리

문제 지점:
- `lib/api/admin.ts`가 `app/admin/_data/mock-admin`을 직접 import.
- API module 안에서 mock data, mapper, real request가 섞임.

처리:
- real API 함수와 mock API 함수를 파일 단위로 분리.
- public API surface만 같은 이름으로 유지한다.
- `features.mockApi` 분기는 adapter 또는 index 파일에서만 수행한다.

## 작업 순서

1. `pnpm exec tsc --noEmit --pretty false` 에러 목록 고정.
2. `metadata-report-tab.tsx`, `analysis-request-flow.tsx`, `upload-panel.tsx`의 단발성 타입 에러 수정.
3. toast 타입/API 정리.
4. `pnpm exec tsc --noEmit --pretty false` 통과.
5. `next.config.mjs`에서 `ignoreBuildErrors: true` 제거.
6. `pnpm build` 통과.
7. ESLint 의존성/설정 복구 후 `pnpm lint` 통과.
8. 포맷터/상태 라벨 중복 제거.
9. 큰 파일 분리.
10. mock/real API 경계 분리.

## 검증 명령

```bash
corepack pnpm install --frozen-lockfile
corepack pnpm exec tsc --noEmit --pretty false
corepack pnpm build
corepack pnpm lint
git status --short
```

## 완료 기준

- `pnpm exec tsc --noEmit --pretty false` 통과.
- `next.config.mjs`에서 `ignoreBuildErrors` 제거.
- `pnpm build` 통과.
- `pnpm lint` 통과.
- 중복 포맷터/상태 라벨이 공통 helper로 이동.
- 큰 파일 분리는 기능 변화 없이 단계별로 진행.
- mock 데이터가 real API 코드 안에 직접 섞이지 않음.
