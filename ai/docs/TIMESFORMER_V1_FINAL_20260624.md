# TimeSformer v1 최종 후보 정리 (2026-06-24)

## TimeSformer v1 후보 설정

| 항목 | 값 |
|---|---|
| checkpoint | `/home/sk4team/forenShield-ai/models/test/video/timesformer/v1.0.0/timesformer_continuous_sampler_celebdf_head_only_20260624.pth` |
| sampler | continuous |
| clip_frames | 8 |
| sampling_fps | 4 |
| max_clips | 8 |
| aggregation | weighted |
| weighted formula | `0.4 * mean + 0.6 * top30_percent_mean` |
| max_gap_ms | 500 |
| min_valid_face_ratio | 0.75 |
| threshold | 0.5 |

## 동일 98개 기준 최종 비교

`max_clips=4 + mean`에서 score가 나온 동일한 98개 video_id만 기준으로 비교했다.

| experiment_name | eval_count | AUC | accuracy | precision | fake_recall | F1 | TN | FP | FN | TP | fake_mean_score | real_mean_score | fake_median_score | real_median_score | no_face_count | insufficient_clip_count |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| continuous + head-only + max_clips=4 + mean | 98 | 0.6266 | 0.6122 | 0.6038 | 0.6531 | 0.6275 | 28 | 21 | 17 | 32 | 0.5251 | 0.4798 | 0.5218 | 0.4800 | 0 | 0 |
| TimeSformer v1: max_clips=8 + weighted | 98 | 0.6393 | 0.6531 | 0.6190 | 0.7959 | 0.6964 | 25 | 24 | 10 | 39 | 0.5498 | 0.5007 | 0.5530 | 0.4901 | 0 | 0 |

## 전체 평가 기준

| experiment_name | eval_count | AUC | accuracy | precision | fake_recall | F1 | TN | FP | FN | TP | fake_mean_score | real_mean_score | fake_median_score | real_median_score | no_face_count | insufficient_clip_count |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| continuous + head-only + max_clips=4 + mean | 98 | 0.6266 | 0.6122 | 0.6038 | 0.6531 | 0.6275 | 28 | 21 | 17 | 32 | 0.5251 | 0.4798 | 0.5218 | 0.4800 | 2 | 2 |
| TimeSformer v1: max_clips=8 + weighted | 99 | 0.6278 | 0.6465 | 0.6094 | 0.7959 | 0.6903 | 25 | 25 | 10 | 39 | 0.5498 | 0.5038 | 0.5530 | 0.4958 | 1 | 1 |

## Error Summary

| config | FN | FP | 해석 |
|---|---:|---:|---|
| max_clips=4 + mean | 17 | 21 | 더 보수적이고 FP가 조금 낮지만 fake recall이 낮음 |
| max_clips=8 + weighted | 10 | 25 | fake 미탐을 크게 줄였지만 real 오탐이 증가함 |

## Score Distribution Plot

![TimeSformer v1 aggregation plot](images/deepfake-results/timesformer_continuous_sampler_celebdf_head_only_maxclips8_aggregation_eval_20260624_score_distribution.png)

## TimeSformer v1 산출물

| 산출물 | 경로 |
|---|---|
| best checkpoint | `/home/sk4team/forenShield-ai/models/test/video/timesformer/v1.0.0/timesformer_continuous_sampler_celebdf_head_only_20260624.pth` |
| best config / final summary JSON | `ai-forensic/docs/data/timesformer-v1-celebdf/timesformer_v1_celebdf_final_summary_20260624.json` |
| full eval summary CSV | `ai-forensic/docs/data/timesformer-v1-celebdf/timesformer_v1_celebdf_full_eval_summary_20260624.csv` |
| same98 comparison CSV | `ai-forensic/docs/data/timesformer-v1-celebdf/timesformer_v1_celebdf_same98_comparison_20260624.csv` |
| same98 score comparison CSV | `ai-forensic/docs/data/timesformer-v1-celebdf/timesformer_v1_celebdf_same98_score_comparison_20260624.csv` |
| aggregation metrics summary CSV | `ai-forensic/docs/data/timesformer-continuous-aggregation-celebdf/timesformer_continuous_sampler_celebdf_head_only_maxclips8_aggregation_eval_20260624_summary.csv` |
| aggregation metrics summary JSON | `ai-forensic/docs/data/timesformer-continuous-aggregation-celebdf/timesformer_continuous_sampler_celebdf_head_only_maxclips8_aggregation_eval_20260624_summary.json` |
| score distribution plot | `ai-forensic/docs/images/deepfake-results/timesformer_continuous_sampler_celebdf_head_only_maxclips8_aggregation_eval_20260624_score_distribution.png` |
| false negative TOP 20 CSV | `ai-forensic/docs/data/timesformer-v1-celebdf/timesformer_v1_celebdf_false_negative_top20_20260624.csv` |
| false positive TOP 20 CSV | `ai-forensic/docs/data/timesformer-v1-celebdf/timesformer_v1_celebdf_false_positive_top20_20260624.csv` |
| no_face / insufficient clip CSV | `ai-forensic/docs/data/timesformer-v1-celebdf/timesformer_v1_celebdf_no_face_insufficient_clip_cases_20260624.csv` |

## Verdict

- AUC는 큰 변화가 없지만, weighted aggregation으로 threshold 0.5 기준 fake recall과 F1이 개선됨.
- TimeSformer v1은 `max_clips=8 + weighted aggregation`으로 확정 후보.
- 이후 단계는 Xception 결과와 fusion.

