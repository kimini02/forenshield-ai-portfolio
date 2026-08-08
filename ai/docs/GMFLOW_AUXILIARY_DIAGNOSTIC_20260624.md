# GMFlow Auxiliary Diagnostic (2026-06-24)

GMFlow는 최종 딥페이크 분류 모델로 바로 사용하지 않고, 얼굴/영상 움직임 이상을 설명하는 보조 신호로 사용할 수 있는지 진단했다.

이번 분석은 기존 inference 결과만 사용했다. GMFlow 재학습, 새 딥러닝 모델, MLP 학습은 수행하지 않았다. threshold sweep은 운영점 진단용이며 최종 성능 주장으로 사용하지 않는다.

## 결론

- `FINAL_SCORE_INCLUDE`: `no`
- `RECOMMENDED_ROLE`: `AUXILIARY_ONLY`
- `REASON`: raw GMFlow score는 방향성이 약하거나 뒤집혀 있으며, 전체 raw AUC는 `0.4622`, inverted AUC는 `0.5378`이다. threshold를 조정하면 recall은 올릴 수 있지만 FP가 크게 늘어나므로 finalScore에 직접 넣기에는 위험하다. 현재 저장된 품질 메타데이터도 부족해서 신뢰도 gate를 안정적으로 적용하기 어렵다.

## 1. Score Direction

| profile | eval_count | raw AUC | inverted AUC | better | fake mean | real mean | fake median | real median |
|---|---:|---:|---:|---|---:|---:|---:|---:|
| celebdf | 100 | 0.4426 | 0.5574 | inverted | 0.2124 | 0.2809 | 0.1643 | 0.1842 |
| ffpp_vox | 100 | 0.4476 | 0.5524 | inverted | 0.3472 | 0.4069 | 0.2678 | 0.2954 |
| overall | 200 | 0.4622 | 0.5378 | inverted | 0.2798 | 0.3439 | 0.2275 | 0.2375 |

해석: raw GMFlow score는 fake보다 real에서 더 높게 나오는 경향이 있다. 즉 현재 `gmflow_score`를 그대로 높을수록 fake인 점수로 해석하면 안 된다.

## 2. Threshold Sweep

threshold sweep은 전체 기준에서 `analysis_score = 1 - gmflow_score`를 사용했다. 이 값은 방향 진단을 위한 보정 점수이며, 최종 성능으로 주장하면 안 된다.

| diagnostic item | value |
|---|---:|
| best_F1_threshold | 0.65 |
| best_F1 | 0.6718 |
| best_F1 precision | 0.5472 |
| best_F1 fake_recall | 0.8700 |
| best_F1 FP / FN | 72 / 13 |
| precision >= 0.60 조건 | 만족 threshold 없음 |
| FP <= 20 최고 F1 threshold | 0.90 |
| FP <= 20 최고 F1 | 0.0885 |

해석: recall을 높이면 FP가 과하게 증가한다. 반대로 FP를 억제하면 fake recall이 거의 사라진다. 따라서 threshold tuning만으로 GMFlow를 최종 분류기로 쓰기는 어렵다.

## 3. Feature Analysis

| feature | source | available | AUC | inverted AUC | better | fake mean | real mean |
|---|---|---:|---:|---:|---|---:|---:|
| flow_mag_mean | flow_mean | 200 | 0.4740 | 0.5260 | inverted | 0.1844 | 0.4220 |
| flow_mag_std | not_available_in_current_summary | 0 |  |  | unavailable |  |  |
| temporal_jitter | temporal_jitter | 200 | 0.4308 | 0.5692 | inverted | 0.0813 | 0.8191 |
| spatial_inconsistency | flow_mag_pair_range_proxy | 200 | 0.4365 | 0.5635 | inverted | 0.2455 | 2.4739 |
| angle_dispersion | not_available_in_current_summary | 0 |  |  | unavailable |  |  |
| motion_energy | flow_mean_squared_proxy | 200 | 0.4740 | 0.5260 | inverted | 0.1511 | 3.5325 |

현재 저장된 summary에는 `flow_mag_std`, `angle_dispersion`, `face_valid_ratio`, `num_flow_pairs`, `bbox_jitter`가 없다. `spatial_inconsistency`는 `flow_mag_pair_range`, `motion_energy`는 `flow_mean^2` proxy로만 진단했다.

가장 의미 있는 저장 feature는 `temporal_jitter`였지만, inverted AUC도 `0.5692` 수준이라 단독 분류 신호로는 약하다.

## 4. Quality Gate

| condition | metadata_available | count | recommendation |
|---|---:|---:|---|
| face_valid_ratio < 0.75 | false |  | cannot_gate_from_current_summary |
| num_flow_pairs 부족 | false |  | cannot_gate_from_current_summary |
| bbox jitter가 큰 경우 | false |  | cannot_gate_from_current_summary |
| duration < 2.0 sec | true | 0 | can_gate_if_needed |
| duration < 5.0 sec | true | 0 | diagnostic_only |

현재 저장 결과만으로는 `INSUFFICIENT_FLOW_QUALITY`를 안정적으로 부여하기 어렵다. 추후 inference 단계에서 `face_valid_ratio`, `num_flow_pairs`, `bbox_jitter`, `angle_dispersion`, `flow_mag_std`를 함께 저장하면 quality gate로 확장할 수 있다.

## 산출물

- [score direction CSV](/Users/kimmini/sk-final-deepfake/ai-forensic/docs/data/gmflow-auxiliary-diagnostic/gmflow_score_direction_auc_20260624.csv)
- [threshold sweep CSV](/Users/kimmini/sk-final-deepfake/ai-forensic/docs/data/gmflow-auxiliary-diagnostic/gmflow_threshold_sweep_20260624.csv)
- [feature AUC CSV](/Users/kimmini/sk-final-deepfake/ai-forensic/docs/data/gmflow-auxiliary-diagnostic/gmflow_feature_auc_20260624.csv)
- [false positive TOP 20 CSV](/Users/kimmini/sk-final-deepfake/ai-forensic/docs/data/gmflow-auxiliary-diagnostic/gmflow_false_positive_top20_20260624.csv)
- [false negative TOP 20 CSV](/Users/kimmini/sk-final-deepfake/ai-forensic/docs/data/gmflow-auxiliary-diagnostic/gmflow_false_negative_top20_20260624.csv)
- [quality gate CSV](/Users/kimmini/sk-final-deepfake/ai-forensic/docs/data/gmflow-auxiliary-diagnostic/gmflow_quality_gate_candidates_20260624.csv)
- [all predictions CSV](/Users/kimmini/sk-final-deepfake/ai-forensic/docs/data/gmflow-auxiliary-diagnostic/gmflow_all_predictions_diagnostic_20260624.csv)
- [summary JSON](/Users/kimmini/sk-final-deepfake/ai-forensic/docs/data/gmflow-auxiliary-diagnostic/gmflow_auxiliary_diagnostic_summary_20260624.json)

## 최종 사용 방침

GMFlow는 finalScore에 직접 포함하지 않는다. 대신 “얼굴/영상 움직임 이상 근거”를 설명하는 auxiliary evidence로 유지한다. 보고서나 시연에서는 TimeSformer/Xception 판단을 보조하는 해석 자료로 보여주는 것이 가장 안전하다.
