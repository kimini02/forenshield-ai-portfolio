# TimeSformer CelebDF Continuous Sampler Analysis (2026-06-24)

## 실험 목적

기존 TimeSformer adapted weight 결과가 CelebDF에서 낮게 나오는 원인이
clip sampler의 비연속 프레임 구성 때문인지 확인하기 위해,
모델 weight는 바꾸지 않고 sampler만 continuous 방식으로 바꿔 비교했다.

## 고정 조건

| 항목 | 값 |
|---|---:|
| 모델 weight | `timesformer-celebdf-adapt-20260624-011100.pth` |
| 데이터셋 | CelebDF |
| clip_frames | 8 |
| max_clips | 4 |
| target sampling_fps | 4 |
| threshold | 0.5 |
| AUC 계산 | threshold와 무관 |

## 핵심 결과

| sampler | n_scored | no_face | AUC | accuracy | precision | recall | F1 | confusion matrix |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| existing sampler adapted | 100 | 0 | 0.5740 | 0.5400 | 0.5400 | 0.5400 | 0.5400 | TN 27 / FP 23 / FN 23 / TP 27 |
| continuous sampler adapted 4fps | 98 | 2 | 0.5764 | 0.5510 | 0.5556 | 0.5102 | 0.5319 | TN 29 / FP 20 / FN 24 / TP 25 |

## Fake/Real Score 통계

| sampler | label | count | mean | median | std | min | max |
|---|---|---:|---:|---:|---:|---:|---:|
| continuous sampler adapted 4fps | fake | 49 | 0.4947 | 0.5054 | 0.0962 | 0.2939 | 0.6820 |
| continuous sampler adapted 4fps | real | 49 | 0.4647 | 0.4680 | 0.1203 | 0.2473 | 0.7108 |

## Clip Audit 요약

`NON_CONTIGUOUS_CLIP` 기준은 `max_gap_ms > 500`이다.

| sampler | clip_count | NON_CONTIGUOUS_CLIP | ratio | max_gap_median_ms | max_gap_max_ms |
|---|---:|---:|---:|---:|---:|
| existing sampler adapted | 397 | 94 | 0.2368 | 466.666 | 2700.0 |
| continuous sampler adapted 4fps | 391 | 0 | 0.0000 | 266.667 | 266.667 |

## Verdict

continuous sampler는 `NON_CONTIGUOUS_CLIP` 문제를 0%로 줄였지만,
CelebDF AUC는 0.5740에서 0.5764로 거의 변하지 않았다.

따라서 현재 CelebDF 성능 문제는 단순히 clip이 띄엄띄엄 뽑힌 문제만은 아니다.
fake/real score 분포가 여전히 많이 겹치므로 threshold 튜닝만으로는 한계가 있고,
CelebDF 도메인에서 score ranking 자체를 개선하는 방향이 필요하다.

다음 우선순위는 face detector/crop 안정화, CelebDF 중심 재학습,
그리고 clip-level score aggregation 및 hard false case 분석이다.

## 산출물

### Data

- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_continuous_celebdf_predictions_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_continuous_celebdf_fake_real_stats_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_existing_vs_continuous_celebdf_fake_real_stats_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_existing_vs_continuous_celebdf_metrics_comparison_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_existing_vs_continuous_celebdf_metrics_comparison_20260624.json`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_continuous_celebdf_clip_audit_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_continuous_celebdf_non_contiguous_summary_20260624.json`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_continuous_celebdf_false_negative_top20_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_continuous_celebdf_false_positive_top20_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_existing_sampler_celebdf_false_negative_top20_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_existing_sampler_celebdf_false_positive_top20_20260624.csv`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_continuous_celebdf_verdict_20260624.txt`
- `ai-forensic/docs/data/timesformer-continuous-celebdf/timesformer_continuous_celebdf_verdict_20260624.json`

### Figures

- `ai-forensic/docs/images/deepfake-results/timesformer_continuous_celebdf_score_distribution_20260624.png`
- `ai-forensic/docs/images/deepfake-results/timesformer_existing_vs_continuous_celebdf_score_distribution_20260624.png`

