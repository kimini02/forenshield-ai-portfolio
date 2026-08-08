# 김민희 AI 모델 발표 학습 메모

## 1. 전체 접근 방향

ForenShield AI는 영상 딥페이크를 한 가지 모델만으로 판단하지 않고, 세 가지 관점으로 나누어 접근한다.

```text
1. 공간적 흔적: 프레임 안의 얼굴 합성 흔적
2. 시간적 흐름: 여러 프레임 사이의 불일치
3. 움직임 이상: 프레임 간 움직임의 어색함
```

따라서 현재 모델은 다음 세 가지로 나누어 선정했다.

| 관점 | 모델 | 역할 |
|---|---|---|
| 공간적 특징 | Xception | 얼굴 crop 이미지 안의 합성 흔적 탐지 |
| 시간적 특징 | TimeSformer | 프레임 시퀀스의 시간적 불일치 탐지 |
| 움직임 특징 | GMFlow | Optical Flow 기반 움직임 이상 보조 분석 |

발표 핵심 문장:

> 저희는 영상 딥페이크 탐지를 공간적 특징, 시간적 특징, 움직임 특징 세 가지 관점으로 나누어 접근했습니다.

---

## 2. Xception 모델

### 모델 역할

Xception은 CNN 기반 모델이다. 영상 전체를 한 번에 보는 것이 아니라, 영상에서 프레임을 뽑고 얼굴 영역을 crop한 뒤, 그 이미지 안에 딥페이크 흔적이 있는지 판단한다.

Xception이 보는 특징:

- 얼굴 경계가 어색한지
- 피부 질감이나 픽셀 패턴이 부자연스러운지
- 합성 과정에서 생기는 공간적 위조 흔적이 있는지
- 프레임 단위로 fake 가능성이 높은지

쉽게 말하면, Xception은 **한 장의 얼굴 이미지 안에서 합성 흔적을 찾는 모델**이다.

### 발표 문장

> Xception은 프레임 단위로 얼굴 영역을 분석하는 CNN 모델입니다. 얼굴 합성 과정에서 생길 수 있는 경계 불연속, 피부 질감, 픽셀 패턴 같은 공간적 위조 흔적을 탐지하기 위해 선정했습니다.

### 현재 성능 해석

현재 Xception의 AUC는 약 `0.686` 수준이다.

AUC는 fake와 real을 구분하는 능력을 의미한다.

```text
AUC 0.5: 랜덤에 가까움
AUC 1.0: 완벽한 구분
```

따라서 Xception은 일정 수준의 분류 성능은 있지만, 단독으로 최종 판정을 맡기기에는 추가 개선이 필요하다.

### 성능 개선 방향

Xception 성능 개선은 모델 재학습보다 먼저 얼굴 전처리 품질을 확인하는 것이 중요하다.

우선 확인할 것:

- Haar 얼굴 검출 성공률
- fake와 real 사이 얼굴 검출률 차이
- 얼굴 crop 크기와 margin
- 흐릿하거나 품질 낮은 frame 제외 여부
- 32프레임 uniform sampling이 적절한지
- 낮은 품질 crop을 평균 점수 계산에서 제외할지

개선 방법:

- Haar Cascade 대신 RetinaFace, MTCNN, MediaPipe 같은 얼굴 검출기 검토
- 얼굴 crop에 턱, 이마, 헤어라인이 적절히 포함되도록 margin 조정
- blur가 심한 프레임 제거
- 얼굴 검출 실패 프레임 처리 방식 명확화
- Celeb-DF 기준으로 threshold 재조정
- 필요 시 fine-tuning 검토

---

## 3. TimeSformer 모델

### 모델 역할

TimeSformer는 Temporal 모델이다. Xception이 프레임 한 장을 본다면, TimeSformer는 여러 프레임의 흐름을 본다.

TimeSformer가 보는 특징:

- 앞뒤 프레임에서 얼굴이 자연스럽게 이어지는지
- 표정 변화가 갑자기 튀지 않는지
- 입 모양이나 얼굴 움직임이 시간적으로 어색하지 않은지
- 프레임 시퀀스에 불일치가 있는지

쉽게 말하면, TimeSformer는 **영상의 시간 흐름 속에서 어색함을 찾는 모델**이다.

### 발표 문장

> TimeSformer는 프레임 하나가 아니라 여러 프레임의 연속성을 분석하는 Temporal 모델입니다. 딥페이크 영상에서 나타날 수 있는 표정 변화, 얼굴 움직임, 프레임 간 불일치 같은 시간적 특징을 보기 위해 선정했습니다.

### 현재 설정

현재 TimeSformer는 다음 방식으로 동작한다.

```text
1. 영상 전체에서 32프레임 uniform sampling
2. Haar로 얼굴 검출
3. 얼굴이 검출된 프레임을 8개씩 묶어 clip 생성
4. 최대 4개 clip 사용
5. clip별 점수를 평균해 video score 계산
```

현재 주요 설정:

```text
SAMPLE_FRAMES = 32
CLIP_FRAMES = 8
MAX_CLIPS = 4
```

평가 시 clip 선택은 random이 아니라 deterministic하게 이루어진다.

### 현재 성능 해석

현재 TimeSformer의 AUC는 약 `0.712`로, 세 모델 중 가장 높은 편이다.

따라서 최종 위험 점수 계산에서는 TimeSformer를 중심 모델 중 하나로 활용할 수 있다.

### 성능 개선 방향

TimeSformer는 temporal 모델이기 때문에 프레임 샘플링 전략이 중요하다.

우선 확인할 것:

- clip 길이 8프레임이 적절한지
- 32프레임 sampling이 영상 길이에 따라 너무 sparse하지 않은지
- max_clips 4개가 충분한지
- 평가와 학습에서 sampling 방식이 동일한지
- train, validation, test가 명확히 분리되어 있는지
- 같은 identity나 같은 원본 영상이 섞여 있지 않은지

개선 방법:

- clip_frames를 8, 16 등으로 비교 실험
- max_clips 증가 실험
- 영상 길이에 따른 sampling interval 조정
- backbone 일부 freeze 후 fine-tuning
- mixed precision 사용
- overfitting 방지를 위해 작은 learning rate 사용
- Celeb-DF, FF++, DFDC 등 데이터셋별 성능 분리 평가

---

## 4. GMFlow 모델

### 모델 역할

GMFlow는 Optical Flow 기반 모델이다. Optical Flow는 연속된 프레임 사이에서 픽셀이 어떻게 움직였는지를 계산한다.

GMFlow가 보는 특징:

- 프레임 간 움직임이 자연스러운지
- 얼굴 움직임이 갑자기 튀는지
- 얼굴 영역과 주변 영역의 움직임이 어색하게 다른지
- 시간적으로 흔들림이나 불연속이 있는지

쉽게 말하면, GMFlow는 **영상의 움직임 이상을 보는 모델**이다.

### 중요한 점

GMFlow는 Xception이나 TimeSformer처럼 학습된 fake/real 분류기로 보기 어렵다.

현재 성능도 AUC 약 `0.450`으로 단독 분류 성능은 낮다.

따라서 GMFlow는 주 분류 모델이 아니라 **움직임 이상을 설명하는 보조 신호**로 활용하는 것이 적절하다.

### 발표 문장

> GMFlow는 딥페이크를 직접 분류하는 모델이라기보다, 프레임 간 움직임 정보를 분석하는 Optical Flow 모델입니다. 따라서 주 분류 모델보다는 영상의 움직임 이상 여부를 보조적으로 설명하는 근거로 활용하려고 합니다.

### 성능 개선 방향

GMFlow는 단독 성능을 무리하게 높이기보다, 보조 feature로 잘 정리하는 것이 중요하다.

우선 확인할 것:

- 프레임 간 실제 시간 간격
- FPS가 다른 영상 처리 방식
- scene cut 제거 여부
- 카메라 움직임 보정 여부
- 얼굴 ROI 기준 flow 분석 여부
- real/fake에서 flow feature 방향이 일관적인지

개선 방법:

- 전체 화면 flow가 아니라 얼굴 ROI 중심 flow 분석
- scene cut pair 제거
- global camera motion 제거
- temporal jitter, spatial inconsistency, angle dispersion 등을 별도 feature로 사용
- 최종 점수에는 낮은 비중으로 반영
- 상세페이지에서는 "움직임 이상" 근거로 표시

---

## 5. 세 모델을 같이 쓰는 이유

딥페이크 영상은 한 가지 특징만으로 판단하기 어렵다.

각 모델이 보는 정보가 다르다.

| 모델 | 보는 정보 |
|---|---|
| Xception | 프레임 안의 얼굴 합성 흔적 |
| TimeSformer | 여러 프레임 사이의 시간적 불일치 |
| GMFlow | 프레임 간 움직임 이상 |

따라서 세 모델을 같이 사용하면 한 모델이 놓치는 부분을 다른 모델이 보완할 수 있다.

발표 문장:

> 영상 딥페이크는 프레임 이미지 안의 흔적만으로 판단하기 어렵기 때문에, 저희는 공간적 특징을 보는 Xception, 시간적 흐름을 보는 TimeSformer, 움직임 이상을 보는 GMFlow로 역할을 나누었습니다. 최종적으로는 Xception과 TimeSformer를 중심으로 위험 점수를 계산하고, GMFlow는 움직임 이상을 설명하는 보조 신호로 활용할 계획입니다.

---

## 6. 현재 성능 정리

현재 벤치마크 결과는 대략 다음과 같다.

| 모델 | 역할 | AUC | 해석 |
|---|---|---:|---|
| Xception | 공간적 위조 흔적 | 0.686 | 일정 수준의 분류 성능 |
| TimeSformer | 시간적 불일치 | 0.712 | 현재 가장 높은 성능 |
| GMFlow | 움직임 이상 | 0.450 | 단독 판정보다는 보조 신호 |

발표 문장:

> 현재 벤치마크에서는 TimeSformer가 가장 높은 AUC를 보였고, Xception도 일정 수준의 분류 성능을 보였습니다. 반면 GMFlow는 단독 분류 성능은 낮았기 때문에, 움직임 이상을 설명하는 보조 지표로 활용하는 방향이 적절하다고 판단했습니다.

---

## 7. 성능 개선에서 가장 먼저 볼 것

모델을 바로 더 학습시키기 전에, 먼저 데이터와 전처리 품질을 확인해야 한다.

AI 모델 성능이 낮은 이유가 모델 자체 때문이 아닐 수 있기 때문이다.

예를 들어:

- fake와 real의 해상도나 FPS가 다르다.
- fake는 얼굴 검출이 잘 되는데 real은 얼굴 검출이 잘 안 된다.
- train과 test에 같은 인물이나 같은 원본 영상이 섞여 있다.
- 같은 test set을 반복해서 보면서 threshold를 맞췄다.

이런 경우 성능이 좋아 보여도 실제 일반화 성능은 신뢰하기 어렵다.

### 우선 확인해야 하는 5가지

1. parent source 기준 데이터 manifest
2. Haar 얼굴 검출 통계
3. TimeSformer의 실제 temporal stride
4. 현재 200개 test set의 반복 사용 여부
5. 운영 영상의 위조 유형 비율

---

## 8. 현재 확인된 데이터 상태

### 데이터 manifest

현재 파일 기준 manifest는 존재한다.

```text
FF++ fake 50:
data/test/video/ffpp/fake_over60s/manifest.json

VoxCeleb real 50:
data/test/video/voxceleb/real/manifest.json

Celeb-DF 100:
data/test/video/celeb-df-v2/manifest.json
```

확인된 내용:

- FF++ fake는 source_path 50개가 모두 unique
- VoxCeleb real은 video_id 50개가 모두 unique
- Celeb-DF는 source 100개가 모두 unique

부족한 내용:

- FF++ fake에 대응되는 pristine 원본 경로
- source identity
- target identity
- parent source video 기준 그룹 정보

따라서 현재 manifest는 파일 기준으로는 정리되어 있지만, 데이터 누수 검증을 위한 parent source / identity 기준 manifest는 부족하다.

### Haar 얼굴 검출 통계

현재 OpenCV Haar Cascade를 사용한다.

32프레임을 uniform sampling한 뒤 얼굴 crop을 수행한다.

label별 얼굴 검출률:

| 모델/프로필 | fake | real |
|---|---:|---:|
| TimeSformer - Celeb-DF | 97.0% | 95.9% |
| TimeSformer - FF++/Vox | 96.3% | 75.2% |
| Xception - FF++/Vox | 97.9% | 79.0% |
| Xception - Celeb-DF | 97.5% | 96.8% |

중요한 해석:

> FF++/Vox 프로필에서는 real인 VoxCeleb 쪽 얼굴 검출률이 낮다. 따라서 모델이 딥페이크 특징이 아니라 얼굴 검출 품질 차이나 데이터셋 품질 차이를 학습했을 가능성을 점검해야 한다.

### TimeSformer temporal stride

현재 TimeSformer는 원본 FPS 기준 고정 stride를 쓰지 않는다.

처리 방식:

```text
1. 영상 전체에서 32프레임 uniform sampling
2. 얼굴 검출된 프레임만 사용
3. 8프레임씩 묶어 clip 생성
4. 최대 4개 clip 사용
```

즉 실제 temporal stride는 영상 길이에 따라 달라진다.

예:

```text
30초 영상: 샘플 간격 약 1초
60초 영상: 샘플 간격 약 2초
```

### Test set 반복 사용 여부

현재 200개 test set은 여러 번 사용되었다.

예:

- TimeSformer Celeb-DF run 여러 번
- Xception FF++/Vox, Celeb-DF 여러 번
- GMFlow/PWC-Net threshold 분석에도 사용

따라서 현재 200개는 완전한 최종 holdout test라기보다 반복 사용된 evaluation set으로 보는 것이 맞다.

### 운영 영상 위조 유형 비율

아직 실제 운영 영상 분포는 정의되어 있지 않다.

현재 fake 데이터는 주로:

- FF++ DeepFakeDetection
- Celeb-DF Celeb-synthesis

중심이다.

운영에서는 다음 유형이 섞일 수 있다.

- face-swap
- lip-sync
- full-generated
- splice
- 얼굴 없는 영상
- 여러 명 등장 영상
- 오디오 포함 영상

이 비율은 추후 운영 데이터나 시나리오를 기준으로 별도 정의가 필요하다.

---

## 9. 성능 개선 우선순위

### 1순위: 데이터 누수 확인

가장 먼저 확인할 것:

- train, validation, test에 같은 원본 영상이 섞였는지
- 같은 identity가 섞였는지
- 같은 parent source에서 파생된 fake가 test에 같이 들어갔는지
- Celeb-DF 100개가 공식 test list인지 자체 sampling인지
- real/fake의 codec, FPS, 해상도, duration 차이가 큰지

### 2순위: 얼굴 전처리 개선

확인할 것:

- Haar 검출 성공률
- no_face 처리 방식
- 여러 얼굴이 나올 때 선택 기준
- crop 흔들림
- crop margin
- face resolution

### 3순위: TimeSformer sampling 개선

확인할 것:

- clip_frames
- max_clips
- sampling interval
- deterministic 평가
- clip score 집계 방식

### 4순위: Xception과 TimeSformer 조합

확인할 것:

- 두 모델의 score correlation
- 한 모델만 맞힌 영상 수
- 둘 다 틀린 영상 수
- raw logit 저장 여부

### 5순위: GMFlow 보조 신호화

확인할 것:

- scene cut 제거
- global camera motion 제거
- 얼굴 ROI flow
- profile별 feature 방향성

---

## 10. 최종 점수 계산 방향

현실적인 초기 공식 예시:

```text
finalScore =
  0.45 * TimeSformer_score
+ 0.40 * Xception_score
+ 0.15 * GMFlow_motion_score
```

다만 GMFlow는 현재 단독 성능이 낮기 때문에 처음부터 강하게 반영하면 안 된다.

더 안전한 방식:

```text
finalScore =
  0.55 * TimeSformer_score
+ 0.45 * Xception_score
```

그리고 GMFlow는 별도 근거로 표시한다.

```text
motionAnomaly = GMFlow_motion_score
```

발표에서는 이렇게 말하는 것이 안전하다.

> 최종 위험 점수는 Xception과 TimeSformer를 중심으로 계산하고, GMFlow는 단독 판정이 아니라 움직임 이상을 설명하는 보조 신호로 분리해 활용할 계획입니다.

---

## 11. 상세페이지에 연결되는 값

상세페이지에서 보여줄 주요 값은 다음과 같이 연결할 수 있다.

| 상세페이지 값 | AI 계산 방식 |
|---|---|
| 최종 위변조 점수 | Xception + TimeSformer 중심 ensemble |
| 분석 신뢰도 | 모델 간 합의도 + 얼굴 검출률 + threshold 거리 |
| 품질 점수 | 해상도, blur, FPS, 얼굴 검출률, usable frame 비율 |
| 프레임별 위험도 | Xception frame score + TimeSformer clip score |
| 의심 구간 | threshold 이상 frame/clip을 시간 구간으로 묶음 |
| 대표 프레임 | 위험 점수가 높은 프레임 중 시간 간격을 두고 3개 선택 |
| 히트맵 | Xception Grad-CAM 또는 MVP용 heatmap overlay |
| 탐지 근거 | 모델 점수와 heuristic feature를 설명 문장으로 변환 |

---

## 12. 발표용 최종 요약 문장

> 저희는 영상 딥페이크 탐지를 공간적 특징, 시간적 특징, 움직임 특징 세 가지 관점으로 나누어 접근했습니다.

> Xception은 프레임 단위 얼굴 crop에서 합성 흔적을 보는 CNN 모델입니다.

> TimeSformer는 여러 프레임의 연속성을 분석해 시간적 불일치를 탐지하는 모델입니다.

> GMFlow는 프레임 간 움직임을 분석하는 Optical Flow 모델로, 단독 판정보다는 움직임 이상을 설명하는 보조 신호로 활용합니다.

> 현재 성능 개선은 모델 재학습 이전에 데이터 누수, 얼굴 검출률, 프레임 샘플링, threshold를 먼저 점검하는 방향으로 진행하고 있습니다.

> 최종적으로는 Xception과 TimeSformer를 중심으로 위험 점수를 계산하고, GMFlow는 움직임 이상 해석 근거로 활용할 계획입니다.
