# 리팩토링 2 — mock/sample 격리

> 순서: 01-utils-wiring → **이 문서(중위험)** → 03-component-split
> 대상 repo: `/Users/kimmini/sk-final-deepfake/frontend-forensic`

## 목표

실제 API 화면에 섞여 있는 **sample/mock 데이터 생성 로직을 격리**하고,
**real API 모드에서 가짜 결과가 진짜처럼 보이지 않게** 한다.

핵심 원칙(기능명세 + 리팩토링 지시):
- mock은 `features.mockApi`(=`NEXT_PUBLIC_USE_MOCK_API==='true'`)일 때만 사용.
- 분석 결과가 없으면 **가짜 차트/점수를 만들지 말고** 명확한 상태 UI(`분석 결과 없음` / `분석 대기` / `AI 연동 전`)를 표시.

## 절대 금지

- `git reset --hard`, `git checkout --`, `git restore`, GitHub push
- 사용자 로컬 변경 되돌리기
- mock 모드(`NEXT_PUBLIC_USE_MOCK_API=true`) 동작을 깨뜨리기

## 현재 상태 (문제 지점)

실데이터가 없을 때 `riskScore`에서 **더미를 파생**해 진짜 결과처럼 그리는 코드가 섞여 있음:

| 파일 | 더미 생성 |
|---|---|
| `components/analysis-result.tsx` | `sampleFrameRisks`, `sampleReasonGroups`, `buildSampleResultData` |
| `app/cases/[id]/page.tsx` | `buildSuspiciousSegments`, `buildDetectionFindings`, `fallbackModules`, `buildFrameRisks` (riskScore 기반 파생) |
| `app/evidences/[id]/page.tsx` | `buildResultData`가 `sampleFrameRisks`/`sampleReasonGroups` 사용 |
| `lib/mock-forensic-api.ts` | mock 전체 |
| `app/mypage/_data/mock-cases.ts`, `app/admin/_data/mock-admin.ts` | mock 데이터 |

> 현재 `.env.local`은 `NEXT_PUBLIC_USE_MOCK_API=false`(real). 그런데 위 파생 로직은 real 모드에서도 그려져서 **가짜가 진짜처럼 보이는** 상태.

## 작업

### A. mock 데이터 격리

- mock 데이터/생성기를 `lib/mock/` (또는 각 route의 `_mock/`)로 모은다.
  - 예: `lib/mock/forensic.ts`, `app/mypage/_mock/cases.ts`, `app/admin/_mock/admin.ts`
- 실제 화면에서 mock import를 제거하고, mock 경유는 **API 모듈 레이어에서만**(`features.mockApi`일 때) 처리.

### B. real 모드 가짜 결과 제거

- `analysis-result.tsx`의 `sampleFrameRisks`/`sampleReasonGroups`/`buildSampleResultData`는
  **데모/mock 전용**임을 명확히 하고, real 화면이 직접 쓰지 않게 한다.
- `cases/[id]`·`evidences/[id]`에서 `riskScore`로 프레임/구간/탐지근거를 **파생**하는 부분:
  - 백엔드가 실제 `frame_scores`/`suspicious_segments`/`detection_reasons`를 주면 그 값만 표시.
  - 없으면 빈 상태 UI:
    - `analysisInfo.status`가 `PENDING`/`PROCESSING` → "분석 대기 / 분석 중"
    - `COMPLETED`인데 근거 데이터가 없음 → "분석 근거 없음" (RQ-DTL-089)
    - AI 미연동 구간 → "AI 연동 전"

### C. 게이트

```ts
import { features } from "@/lib/features"
// mock은 여기서만:
if (features.mockApi) { /* mock */ } else { /* real */ }
```

각 단계 후 `pnpm build`.

## 주의 / 함정

- **이 단계가 behavioral change가 가장 크다.** 한 화면씩, mock 모드/real 모드 둘 다 깨지지 않게.
- `cases/[id]`의 탭들은 현재 더미에 강하게 의존 → 빈 상태 UI 분기를 먼저 넣고, 더미 파생을 mock 게이트 뒤로 옮긴다.
- 기능명세(RQ-DTL-089 "분석 근거 없음", 060/061/062~066 표시 항목)는 **데이터 있을 때만** 채운다.

## 완료 기준

- real 모드에서 sample 결과가 진짜 결과처럼 보이지 않는다.
- mock 모드와 real 모드가 코드상 명확히 구분(`features.mockApi` 게이트).
- 결과 없음/대기/AI 연동 전 상태 UI가 명확.
- `pnpm build` 통과, mock 모드도 정상.

## 최종 보고

- 격리한 mock 파일 / 제거한 real-모드 더미
- real vs mock 분기 위치
- `pnpm build` 결과, `git status --short`
