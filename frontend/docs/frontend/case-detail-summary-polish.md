# 사건 상세 분석 요약 UI 수정 계획

## 목표

상세 페이지 첫 화면을 레퍼런스 화면처럼 가볍고 명확하게 정리한다.

- 최종 위험도 점수와 위험 라벨을 분리해서 보여준다.
- `riskLevel`이 있으면 `riskScore`보다 우선해서 위험 라벨을 결정한다.
- raw AI summary가 요약 카드에서 크게 튀어나오지 않게 한다.
- 분석 요약 카드의 글씨 굵기와 크기를 낮춰 정보 위계를 정리한다.
- 레퍼런스처럼 `판정 요약` 카드에 위험 등급, 분석 신뢰도, 품질 점수, 간단한 게이지를 배치한다.

## 수정 대상

```text
app/cases/[id]/page.tsx
app/cases/[id]/_lib/evidence-display.ts
app/cases/[id]/_components/evidence-summary-card.tsx
app/cases/[id]/_components/summary-tab.tsx
app/cases/[id]/_components/summary-meta-item.tsx
app/cases/[id]/_components/deepfake-model-tab.tsx
```

## 문제 지점

### 1. 위험도 라벨 결정

현재는 `riskScore` 기준으로 `정상/주의/위험`을 결정한다.
백엔드가 `riskLevel`을 주는 경우에는 이 값이 더 명시적인 판정이므로 우선 사용해야 한다.

### 2. 분석 요약 탭의 raw summary

현재 `분석 결과 요약` 카드가 raw AI 문장을 길게 보여준다.
레퍼런스 화면은 첫 요약 영역에서 raw 문장보다 판정 정보와 요약 메트릭을 먼저 보여준다.

### 3. 폰트 굵기

요약 카드 내부 값이 전반적으로 `font-black`, `font-bold` 위주라 화면이 무겁다.
제목은 `font-semibold`, 값은 `font-medium` 또는 `font-semibold` 정도로 낮춘다.

### 4. 품질 점수 계산

기존 `confidence - 1` 계산은 의미가 불명확하다.
전용 품질 점수 필드가 없으면 신뢰도 정규화 값을 `n / 100` 형태로 표시한다.

## 완료 기준

- 최종 위험도 카드에 숫자와 `정상/주의/위험` 라벨이 함께 보인다.
- `판정 요약` 카드에 위험 등급, 분석 신뢰도, 품질 점수, 게이지가 표시된다.
- 분석 요약 탭에서 raw summary가 큰 카드로 튀어나오지 않는다.
- 정보 텍스트는 레퍼런스처럼 작고 연하게 보인다.
- API 호출, 데이터 로딩, mock/real 정책은 변경하지 않는다.
