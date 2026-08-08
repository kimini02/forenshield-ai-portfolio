# GMFlow 움직임 이상 보조 지표 가이드

> 작성 기준일: 2026-06-24  
> 대상: `video-benchmark-datasets/gmflow/{celebdf|ffpp_vox}/<RUN_ID>/`  
> GPU 파이프라인: `scripts/infer/optical_flow_infer_model.py` -> `scripts/infer/optical_flow_common.py`

## 운영 방침

GMFlow는 fake/real classifier가 아니다.
GMFlow는 프레임 간 얼굴/영상 움직임 불안정성을 분석하는 `motionInstabilityScore` 보조 지표로만 사용한다.

| 항목 | 값 |
|---|---|
| FINAL_SCORE_INCLUDE | no |
| RECOMMENDED_ROLE | AUXILIARY_ONLY |
| role | `MOTION_AUXILIARY` |
| gmflowStatus | `AUXILIARY_ONLY` |
| contributesToFinalScore | `false` |
| scoreMeaning | `MOTION_INSTABILITY_NOT_FAKE_PROBABILITY` |

GMFlow 결과는 finalScore에 포함하지 않는다.
`motionInstabilityScore`는 딥페이크 확률이 아니라 움직임 불안정성 수준이다.

## JSON 상태 필드

GMFlow 결과는 처리 상태, 모델 역할, 품질 상태를 분리해서 저장한다.

| 필드 | 값 | 의미 |
|---|---|---|
| `processingStatus` | `OK` 또는 `FAILED` | GMFlow 분석 실행 성공 여부 |
| `gmflowStatus` | `AUXILIARY_ONLY` | GMFlow의 운영 역할 |
| `qualityStatus` | `AVAILABLE` 또는 `INSUFFICIENT_FLOW_QUALITY` | 움직임 분석 품질 상태 |

기존 호환성 때문에 일부 파일에는 legacy `status: "ok"`가 남아 있을 수 있다.
프론트/백엔드/보고서에서는 새 필드인 `processingStatus`, `gmflowStatus`, `qualityStatus`를 우선 사용한다.

## motionInstabilityScore

`motionInstabilityScore`는 움직임 불안정성 보조 지표다.
사람이 읽기 쉽도록 `motionInstabilityLevel`을 함께 저장한다.

| level | 기준 | 의미 |
|---|---:|---|
| LOW | `0.00 <= score < 0.35` | 움직임 불안정성이 낮음 |
| MEDIUM | `0.35 <= score < 0.65` | 움직임 불안정성이 중간 |
| HIGH | `0.65 <= score` | 움직임 불안정성이 높음 |

이 level은 딥페이크 위험도가 아니다.
카메라 흔들림, 큰 고개 움직임, 얼굴 검출 불안정 같은 정상 영상 조건에서도 높아질 수 있다.

## 권장 JSON 구조

```json
{
  "file": "celebdf_fake_001.mp4",
  "ground_truth_label": "fake",
  "model": "gmflow",
  "modelName": "GMFlow",
  "role": "MOTION_AUXILIARY",

  "processingStatus": "OK",
  "gmflowStatus": "AUXILIARY_ONLY",
  "qualityStatus": "AVAILABLE",

  "contributesToFinalScore": false,

  "gmflowRawScore": 0.062791,
  "motionInstabilityScore": 0.062791,
  "motionInstabilityLevel": "LOW",
  "scoreMeaning": "MOTION_INSTABILITY_NOT_FAKE_PROBABILITY",

  "qualityMetadata": {
    "face_valid_ratio": null,
    "num_flow_pairs": 31,
    "bbox_jitter": null,
    "flow_mag_mean": 0.1844,
    "flow_mag_std": null,
    "temporal_jitter": 0.0813,
    "spatial_inconsistency": 0.2455,
    "angle_dispersion": null,
    "duration": 3.24,
    "fps": 29.97
  },

  "display": {
    "title": "GMFlow 움직임 분석",
    "label": "움직임 불안정성",
    "badge": "보조 분석",
    "includedInFinalScore": "아니오",
    "description": "GMFlow는 딥페이크 확률이 아니라 프레임 간 얼굴 움직임 불안정성을 분석하는 보조 지표입니다."
  },

  "explanation": "GMFlow는 딥페이크 확률이 아니라 프레임 간 얼굴 움직임 불안정성을 분석하는 보조 지표입니다. 이 값은 최종 위험도 산출에 직접 반영되지 않으며, Xception과 TimeSformer 결과를 해석하는 참고 근거로 사용됩니다.",

  "limitations": [
    "카메라 흔들림, 큰 고개 움직임, 얼굴 검출 불안정이 있는 정상 영상에서도 움직임 불안정성 점수가 높게 나올 수 있습니다.",
    "본 지표는 최종 딥페이크 위험도 산출에 직접 반영되지 않습니다.",
    "face_valid_ratio, bbox_jitter 등 품질 메타데이터가 없는 경우 GMFlow 결과의 품질 판단은 제한적입니다."
  ]
}
```

## qualityMetadata

GMFlow 결과에는 아래 품질 메타데이터를 저장한다.
현재 값이 없는 항목은 `null`로 저장한다.

| 필드 | 의미 |
|---|---|
| `face_valid_ratio` | 분석 clip/frame 중 얼굴이 유효하게 검출된 비율 |
| `num_flow_pairs` | optical flow를 계산한 프레임 pair 수 |
| `bbox_jitter` | 얼굴 bounding box 흔들림 정도 |
| `flow_mag_mean` | 평균 flow magnitude |
| `flow_mag_std` | flow magnitude 표준편차 |
| `temporal_jitter` | 프레임 간 움직임 크기 변동 |
| `spatial_inconsistency` | 공간적으로 flow가 고르지 않은 정도 |
| `angle_dispersion` | flow 방향 분산 |
| `duration` | 영상 길이 |
| `fps` | 영상 FPS |

특히 아래 값이 `null`이면 quality gate를 안정적으로 적용하기 어렵다.

- `face_valid_ratio`
- `bbox_jitter`
- `flow_mag_std`
- `angle_dispersion`

이 경우 JSON의 `notes` 또는 `qualityMetadataMissing`에 현재 품질 판단이 제한적임을 남긴다.

## quality gate

아래 조건 중 하나라도 만족하면 `qualityStatus`를 `INSUFFICIENT_FLOW_QUALITY`로 둘 수 있다.

| 조건 | reason |
|---|---|
| `face_valid_ratio < 0.75` | `LOW_FACE_VALID_RATIO` |
| `num_flow_pairs` 부족 | `TOO_FEW_FLOW_PAIRS` |
| `bbox_jitter` 과도 | `EXCESSIVE_BBOX_JITTER` |
| `duration` 너무 짧음 | `SHORT_DURATION` |

현재 일부 inference 결과는 `face_valid_ratio`, `bbox_jitter` 등 품질 메타데이터가 `null`이다.
따라서 현재 quality gate는 부분적으로만 적용된다.
추후 inference 단계에서 얼굴 검출 비율, bbox jitter, flow 표준편차, 방향 분산을 안정적으로 저장해야 한다.

## motionTimeline

영상 구간별 움직임 불안정성도 저장할 수 있다.

```json
{
  "motionTimeline": [
    {
      "startMs": 0,
      "endMs": 1935,
      "motionInstabilityScore": 0.12,
      "flow_mag_mean": 0.1844,
      "temporal_jitter": 0.0813,
      "warning": ""
    }
  ],
  "suspiciousMotionSegments": [
    {
      "startMs": 0,
      "endMs": 1935,
      "motionInstabilityScore": 0.12,
      "flow_mag_mean": 0.1844,
      "temporal_jitter": 0.0813,
      "warning": ""
    }
  ]
}
```

`suspiciousMotionSegments`는 `motionInstabilityScore`가 높은 상위 구간 3개를 저장한다.
이 정보도 최종 위험도 계산에는 반영하지 않고, Xception/TimeSformer 결과를 해석하는 참고 근거로만 사용한다.

## 성능 해석 주의

기존 GMFlow 진단에서 raw score와 inverted score를 모두 확인했다.
전체 raw AUC는 낮고, inverted AUC도 최종 분류기로 쓰기에는 충분하지 않았다.
threshold sweep은 운영점 진단용이며 최종 딥페이크 성능으로 주장하면 안 된다.

따라서 GMFlow는 단독 분류 모델이 아니라 `motion instability evidence`로 유지한다.

## 표현 가이드

사용자 화면, 보고서, API 설명에서는 GMFlow를 딥페이크 확률이나 최종 분류 모델처럼 보이게 하는 표현을 쓰지 않는다.
아래 표현을 사용한다.

- GMFlow 움직임 분석
- 움직임 불안정성
- 움직임 이상 보조 지표
- motion instability evidence
- auxiliary evidence

## finalScore 반영 여부

GMFlow JSON에는 항상 아래 값을 포함한다.

```json
{
  "role": "MOTION_AUXILIARY",
  "gmflowStatus": "AUXILIARY_ONLY",
  "contributesToFinalScore": false
}
```

백엔드의 현재 저장 로직은 `deepfakeScore` 중심으로 module score를 저장한다.
GMFlow의 `motionInstabilityScore`는 `deepfakeScore`로 매핑하지 않으며, finalScore 계산 입력으로 사용하지 않는다.

## 추후 작업

현재 quality gate를 더 신뢰성 있게 쓰려면 inference 단계에서 아래 값을 안정적으로 저장해야 한다.

- `face_valid_ratio`
- `bbox_jitter`
- `flow_mag_std`
- `angle_dispersion`

이 값들이 채워지면 `qualityStatus=INSUFFICIENT_FLOW_QUALITY` 판단을 더 정확하게 적용할 수 있다.
