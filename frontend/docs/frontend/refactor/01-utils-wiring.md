# 리팩토링 1 — 공통 유틸 배선

> 순서: **이 문서(저위험) → 02-mock-isolation → 03-component-split**
> 대상 repo: `/Users/kimmini/sk-final-deepfake/frontend-forensic`

## 목표

이미 만들어 둔 공통 유틸 파일들을 **실제 화면에 배선**해서 중복 포맷터/라벨/에러처리를 제거한다.
**한 파일씩 바꾸고 매번 `pnpm build` 확인.** UI는 바꾸지 않는다.

## 절대 금지

- `git reset --hard`, `git checkout --`, `git restore`, GitHub push
- 사용자 로컬 변경 되돌리기
- 대규모 UI 변경

## 현재 상태 (이미 존재, 대부분 미배선)

| 파일 | 제공 |
|---|---|
| `lib/features.ts` | `features.mockApi`, `features.uploadOnlyMode` |
| `lib/formatters.ts` | `formatDateTime`, `formatDateTimeWithSeconds`, `formatFileSize`, `formatDuration` |
| `lib/status-labels.ts` | `getAnalysisStatusLabel`, `getRiskTone`, `getRiskLabel` |
| `lib/api/errors.ts` | `getApiErrorMessage`, `isUnauthorizedError` |

API 클라이언트는 이미 `lib/api/client.ts` 하나로 통일됨.

## 작업

### A. 중복 함수 찾기

```bash
grep -rn "function formatBytes\|function formatFileSize\|toLocaleString\|new Date(" app components lib --include="*.ts" --include="*.tsx" | grep -i "format\|date\|byte"
grep -rn "function getRiskLabel\|function getRiskTone\|function getStatusLabel\|위험\b\|주의\b\|정상\b" app components --include="*.tsx" | head -40
grep -rn "instanceof ApiError" app components --include="*.tsx"
```

주요 중복 위치(확인용):
- `app/cases/[id]/page.tsx`: `formatBytes`, `formatDateTime`, `getRiskTone`/`getRiskLabel`, `scoreTone`
- `app/evidences/[id]/page.tsx`: `formatUploadedAt`, `buildResultData`
- `app/mypage/_components/*`, `app/main/_components/dashboard-overview.tsx`

### B. 한 파일씩 교체 (작은 것부터)

1. 날짜/파일크기 포맷 → `@/lib/formatters` import로 교체
2. 분석 상태/위험도 라벨 → `@/lib/status-labels` 로 교체
3. `catch (e)` 처리 → `getApiErrorMessage(e)` / `isUnauthorizedError(e)` 로 교체
4. `process.env.NEXT_PUBLIC_*` 잔존분 → `features.*` 로 교체

각 단계마다:
```bash
pnpm build   # 통과 확인
```

## 주의 / 함정

- **출력 포맷이 동일한지 확인.** 기존 `formatBytes`가 `0`일 때 `"-"`를 반환하는지 등 미세 차이 → `formatFileSize`도 동일하게 동작(0 이하 `"-"`). 다르면 화면 값이 바뀌니 비교 후 교체.
- 위험도 임계값은 **70/40 기준**으로 통일(`status-labels.ts`). 일부 화면이 45 등 다른 값을 쓰면 의도 확인.
- `lib/api/errors.ts`의 `ApiError`는 표준(`@/lib/api/client`) 기준. 구 클라이언트 잔존 없음.
- 한 번에 전 파일 교체 금지. import 영향 작은 것부터.

## 완료 기준

- 중복 포맷터/라벨/에러처리 함수가 화면에서 제거되고 `lib/*`에서 import.
- `process.env.NEXT_PUBLIC_*` 직접 읽기 0 (전부 `features`).
- `pnpm build` 통과, UI/값 변화 없음.
- 새로 생긴 `tsc` 에러 없음. (기존: toast 계열, `upload-panel.tsx(99,29)` flatMap)

## 최종 보고

- 바꾼 파일 목록 / 제거한 중복 함수
- `pnpm build` 결과
- `git status --short`
