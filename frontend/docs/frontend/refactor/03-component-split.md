# 리팩토링 3 — 거대 컴포넌트 분리

> 순서: 01-utils-wiring → 02-mock-isolation → **이 문서(고위험·마지막)**
> 대상 repo: `/Users/kimmini/sk-final-deepfake/frontend-forensic`

## 목표

500줄을 넘는 거대 파일을 **route의 `_components/` + `_hooks/`로 분리**한다.
**UI/동작은 그대로**, `pnpm build` 통과. 한 파일씩.

## 절대 금지

- `git reset --hard`, `git checkout --`, `git restore`, GitHub push
- 사용자 로컬 변경 되돌리기
- 큰 UI 재설계, 한 번에 여러 파일 동시 분해

## 대상 (큰 순서)

```bash
# 줄 수 확인
wc -l app/cases/[id]/page.tsx app/main/_components/analysis-request-flow.tsx \
      app/compare/_components/compare-verification-flow.tsx components/upload-panel.tsx lib/api/admin.ts
```

### 1. `app/cases/[id]/page.tsx` (가장 큼, ~1600줄)
이미 내부에 함수 컴포넌트가 많음(`EvidenceSummaryCard`, `SummaryTab`, `DeepfakeModelTab`, `IntegrityTab`, `MetadataReportTab`, `RingGauge`, `FrameRiskChart`, `MetaTable`, `ModelResultCard`, `SuspiciousSegmentCard`, `DetectionFindingCard`, `InfoBox`, `ManifestRow` 등).

분리 방향:
```text
app/cases/[id]/_components/
  case-summary-header.tsx        (EvidenceSummaryCard + SummaryMetaItem)
  summary-tab.tsx
  deepfake-model-tab.tsx         (+ RingGauge, FrameRiskChart, ModelResultCard, SuspiciousSegmentCard, DetectionFindingCard)
  integrity-tab.tsx              (+ ManifestRow, 타임라인)
  metadata-report-tab.tsx        (+ MetaTable, VerificationQr)
app/cases/[id]/_lib/
  case-detail.helpers.ts         (포맷/파생 헬퍼: scoreTone, buildSuspiciousSegments 등 — 02 단계와 연계)
```
- `page.tsx`는 **데이터 로딩 + 탭 조립만** 남긴다.
- 공유 표현 컴포넌트(RingGauge/FrameRiskChart/MetaTable)는 여러 탭이 쓰면 `_components/`에 둔다.

### 2. `app/main/_components/analysis-request-flow.tsx` (~1100줄)
```text
app/main/_hooks/use-analysis-upload.ts      (업로드/폴링/상태 로직)
app/main/_components/upload-dropzone.tsx
app/main/_components/upload-preview.tsx
app/main/_components/upload-status-panel.tsx
```
- 업로드/분석 폴링 로직(useEffect, fetchAnalysisStatus, startEvidenceAnalysis, uploadEvidence)을 hook으로.
- 데모용 `frameRisks`/`suspiciousFrameGroups`/`resultPresets`는 02 단계(mock 격리)와 함께 정리.

### 3. `app/compare/_components/compare-verification-flow.tsx`
```text
app/compare/_hooks/use-compare-verification.ts
app/compare/_components/source-evidence-selector.tsx
app/compare/_components/compare-file-uploader.tsx
app/compare/_components/compare-processing-panel.tsx
app/compare/_components/compare-result-panel.tsx
```

### 4. `components/upload-panel.tsx`
- dropzone / preview / status 단위로 분리.
- ⚠️ 기존 `upload-panel.tsx(99,29)` flatMap 타입 에러가 있음 → **분리하면서 같이 정리**(타입을 명시).

### 5. `lib/api/admin.ts`
- 도메인별 모듈로 분리: `lib/api/admin/users.ts`, `admin/logs.ts`, `admin/invite-codes.ts`, `admin/stats.ts` 등.
- 표준 클라이언트(`lib/api/client.ts`)만 사용.

## 작업 방식

1. **순수 표현 컴포넌트부터** 잘라낸다(props만 받는 것). 로직 적어 안전.
2. props/타입을 그대로 유지(export 시그니처 불변).
3. 한 파일 분리 → `pnpm build` → 다음.
4. import 경로만 바뀌고 렌더 결과는 동일해야 함.

## 주의 / 함정

- "use client" 지시어가 필요한 컴포넌트는 분리된 파일 상단에도 유지.
- 같은 이름 헬퍼가 여러 곳에 있으면 한 곳(`_lib` 또는 `lib/`)으로.
- 분리 중 더미/mock가 섞여 있으면 **02 단계와 충돌** → 가능하면 02 먼저.
- 한 커밋/한 파일 단위로 작게. 빌드 깨지면 즉시 원인 설명.

## 완료 기준

- 대상 파일 각각 **500줄 이하**.
- `page.tsx`는 데이터 로딩 + 조립만.
- UI/동작 변화 없음, `pnpm build` 통과.
- 새로 생긴 `tsc` 에러 없음.

## 최종 보고

- 분리한 파일 트리
- 각 파일 줄 수(전/후)
- `pnpm build` 결과, `git status --short`
