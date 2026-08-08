# AI 분석 인터페이스 JSON 명세 (BE ↔ AI)

> **기준:** `docs/requirements/source/기능명세서_최종.xlsx` · AI 시트 · **영상(VIDEO) 전용**  
> **REST API 응답 형식:** [../guides/implementation-standards.md](../guides/implementation-standards.md) · [../api/specification.md](../api/specification.md)

---

## 1. 개요

| 항목 | 값 |
| :--- | :--- |
| 통신 | RabbitMQ (AMQP) |
| 큐 (현재 BE 구현) | `forenshield.analysis.queue` |
| Exchange (목표/확장) | `ai.analysis.exchange` (Topic) — [rabbitmq.md](./rabbitmq.md) |
| 인코딩 | JSON UTF-8 |
| **fileType** | **`video` 만** (음성·이미지 미지원) |

---

## 2. Analysis Request (백엔드 → AI 워커)

| 필드 | 타입 | 필수 | 설명 |
| :--- | :--- | :---: | :--- |
| `analysisRequestId` | Long | ✅ | 분석 요청 PK (`AnalysisRequests`) |
| `evidenceId` | Long | ✅ | 증거 PK |
| `fileType` | String | ✅ | **`video`** |
| `filePath` | String | ✅ | S3 분석 대상 경로 (copy) |
| `originalHash` | String | ✅ | 원본 SHA-256 |
| `caseName` | String | ❌ | 사건명 |
| `requestedAt` | String | ❌ | ISO 8601 UTC |

```json
{
  "analysisRequestId": 1024,
  "evidenceId": 512,
  "fileType": "video",
  "filePath": "cases/128/512/copy/evidence_video.mp4",
  "originalHash": "a3f12e8c9b7d0e1f…",
  "caseName": "2026-서울-0123 딥페이크 유포 사건",
  "requestedAt": "2026-06-08T10:00:00Z"
}
```

---

## 3. Analysis Response (AI 워커 → 백엔드)

### 3.1 공통 필드

| 필드 | 타입 | 설명 |
| :--- | :--- | :--- |
| `analysisRequestId` | Long | 분석 요청 ID |
| `evidenceId` | Long | 증거 ID |
| `status` | String | `IN_PROGRESS` \| `COMPLETED` \| `FAILED` |
| `progressPercent` | Integer | `IN_PROGRESS` 시 0~99 권장. `COMPLETED`면 BE가 100으로 확정 |
| `riskScore` | Double | 0.0 ~ 100.0 |
| `confidenceScore` | Double | 0.0 ~ 1.0 |
| `riskLevel` | String | `LOW` \| `MEDIUM` \| `HIGH` |
| `analysisReasons` | String[] | 판정 근거 문장 |
| `results` | Array | **영상** 모듈 결과 (아래 §4) |
| `analyzedAt` | String | ISO 8601 UTC |

### 3.2 실패 시

| 필드 | 타입 | 설명 |
| :--- | :--- | :--- |
| `errorCode` | String | 예: `MODEL_INFERENCE_FAILED` |
| `message` | String | 사용자/운영용 요약 (시크릿 금지) |

### 3.3 On-demand Overlay Job (선택)

분석 완료 후 FE가 모듈별 baked 오버레이를 요청할 때 사용합니다.

**Job (BE → AI)** queue `forenshield.overlay.queue`, routing key `overlay.video`

| 필드 | 타입 | 설명 |
| :--- | :--- | :--- |
| `jobType` | String | `OVERLAY` |
| `overlayJobId` | Long | BE job id |
| `analysisRequestId` | Long | |
| `evidenceId` | Long | |
| `module` | String | `cnn` \| `temporal` \| `optical` \| `forgery_spatial` |
| `filePath` / S3 / presign | | 분석 job과 동일 |
| `frameRisks` / `clipRisks` / `pairRisks` | Array | 모듈에 맞는 점수 페이로드 |

**Result (AI → BE)** routing key `result.overlay`

| 필드 | 타입 | 설명 |
| :--- | :--- | :--- |
| `jobType` | String | `OVERLAY` |
| `overlayJobId` | Long | |
| `status` | String | `IN_PROGRESS` \| `COMPLETED` \| `FAILED` |
| `progressPercent` | Integer | 0~100 |
| `overlayVideoUrl` | String | COMPLETED 시 필수 |
| `module` | String | |

본 분석 경로에서는 오버레이 MP4를 만들지 않고, TruFor 점수·대표 프레임만 포함합니다.

---

## 4. 영상(Video) results[] 항목

| 필드 | 타입 | 설명 |
| :--- | :--- | :--- |
| `type` | String | **`video`** |
| `lipSyncDetected` | Boolean | 립싱크 불일치 |
| `lipSyncScore` | Double | 0.0 ~ 1.0 |
| `frameEditDetected` | Boolean | 프레임 편집 흔적 |
| `frameEditScore` | Double | |
| `deepfakeDetected` | Boolean | 얼굴 합성 |
| `deepfakeScore` | Double | |
| `splicingDetected` | Boolean | 구간 이어붙이기 |
| `splicingScore` | Double | |
| `reEncodingDetected` | Boolean | 재인코딩 흔적 |
| `reEncodingScore` | Double | |
| `frameRisks[]` | Array | 프레임별 위험 점수 (`frameIndex`, `timestampSec`, `riskScore` 0.0~1.0) |
| `suspiciousSegments[]` | Array | 의심 구간 (`startTime`, `endTime`, `maxRiskScore`, `reason`) |

### 4.1 Analysis Request `frameAnalysis` (BE → AI, SK-401)

| 필드 | 타입 | 설명 |
| :--- | :--- | :--- |
| `extractionIntervalSec` | Double | 프레임 샘플링 간격(초) |
| `highRiskFrameScoreThreshold` | Double | 고위험 프레임 기준 (0.0~1.0) |
| `minSuspiciousSegmentSec` | Double | 의심 구간 최소 길이(초) |
| `pixelFormat` | String | 모델 입력 (`RGB24`) |
| `imageEncoding` | String | 프레임 인코딩 (`jpeg`) |
| `sampleTimestampsSec` | Double[] | 추출·분석 대상 시각(초) |

> RQ-DTL-062~065, RQ-DTL-063(립싱크) 등 **상세 화면**은 위 필드를 API DTO로 매핑합니다.

---

## 5. 위험도 기준

| riskLevel | riskScore | 비고 |
| :--- | :--- | :--- |
| `LOW` | 0.0 ~ 39.9 | 변조 흔적 미미 |
| `MEDIUM` | 40.0 ~ 69.9 | 추가 검토 |
| `HIGH` | 70.0 ~ 100.0 | 변조 가능성 높음 |

---

## 6. 예시 (완료)

```json
{
  "analysisRequestId": 1024,
  "evidenceId": 512,
  "status": "COMPLETED",
  "riskScore": 85.5,
  "confidenceScore": 0.94,
  "riskLevel": "HIGH",
  "analysisReasons": [
    "Deepfake face detection score is exceptionally high (0.92)",
    "Frame editing traces found in sequence 0:12 - 0:15"
  ],
  "results": [
    {
      "type": "video",
      "lipSyncDetected": true,
      "lipSyncScore": 0.78,
      "frameEditDetected": true,
      "frameEditScore": 0.85,
      "deepfakeDetected": true,
      "deepfakeScore": 0.92,
      "splicingDetected": false,
      "splicingScore": 0.12,
      "reEncodingDetected": true,
      "reEncodingScore": 0.65
    }
  ],
  "analyzedAt": "2026-06-08T10:05:00Z"
}
```

---

## 7. 구현 메모

- **BE:** `Jackson2JsonMessageConverter` · DTO는 `@JsonProperty`로 필드명 고정  
- **AI:** Pydantic 등으로 스키마 검증 · `fileType != video` 거부  
- **시각:** UTC ISO 8601 · score는 소수 둘째 자리 권장

재생성·갱신 시 Excel AI 시트와 본 문서를 동시에 수정하세요.
