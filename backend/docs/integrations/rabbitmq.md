# RabbitMQ 분석 파이프라인

> **기준:** `docs/requirements/source/기능명세서_최종.xlsx` · **영상(VIDEO) 분석 전용**  
> **AI JSON 계약:** [ai-json.md](./ai-json.md)

---

## 1. 현재 구현 (backend-forensic)

Spring Boot는 **단일 durable 큐**를 사용합니다.

| 항목 | 값 |
| :--- | :--- |
| Queue | `forenshield.analysis.queue` |
| Config | `RabbitMqConfig.ANALYSIS_QUEUE` |
| Publisher | `RabbitMqAnalysisJobEnqueuer` |
| Consumer (dev) | `RabbitMqAnalysisQueueConsumer` |
| Result Queue | `backend.ai.result.queue` |
| Result Consumer | `RabbitMqAnalysisResultConsumer` |
| Prefetch | `1` (무거운 영상 분석 1건씩) |

### Worker mode (`analysis.worker.mode`)

| mode | RabbitMQ host | 동작 |
| :--- | :--- | :--- |
| `local` (기본) | 없음 또는 있음 | `LocalAnalysisJobEnqueuer` — BE 내부 시뮬레이션 (RabbitMQ 있어도 local이면 큐 미사용) |
| `simulated` | 있음 | `RabbitMqAnalysisQueueConsumer` — 큐 수신 후 BE 내부 시뮬레이션 |
| `ai` | 있음 | 큐 publish + `markDispatchedToAi` → 외부 AI 결과는 `backend.ai.result.queue` 수신 |

활성 조건: `spring.rabbitmq.host` 설정 시 RabbitMQ 빈 활성 (`@ConditionalOnExpression`)

---

## 2. 메시지 페이로드

큐 메시지 body는 [ai-json.md §2 Analysis Request](./ai-json.md)를 따릅니다.

- `fileType`은 **`video`만** 허용  
- 음성·이미지 워커 라우팅 **없음**

---

## 3. 목표 아키텍처 (확장 시)

영상 전용이므로 Topic 라우팅 키도 **video 하나**로 단순화할 수 있습니다.

| 요소 | 이름 | 타입 | 설명 |
| :--- | :--- | :--- | :--- |
| Analysis Exchange | `ai.analysis.exchange` | Topic | 분석 요청 |
| Result Exchange | `ai.result.exchange` | Topic | 결과 수집 |
| Routing Key | `analyze.video` | — | 영상 AI 워커 |
| Result Queue | `backend.ai.result.queue` | — | BE 결과 수집 |
| DLX | `ai.dead.exchange` | Direct | 3회 실패 격리 |

---

## 4. 신뢰성

- **Persistent messages** (delivery mode 2)  
- **Prefetch 1** — GPU/CPU 부하 분산  
- **Retry:** 최대 3회 → DLX  
- **실패 시 BE:** `AnalysisRequest` → `FAILED`, CoC `ERROR_OCCURRED` ([COC_LOG_PROGRESS.md](../COC_LOG_PROGRESS.md))

---

## 5. 명세와의 관계

| 문서 | 내용 |
| :--- | :--- |
| RQ-REQ-049 | 비동기 AI 분석 큐 연동 |
| Excel AI 시트 | 영상 딥페이크·편집 탐지 모듈 |
| 구버전 초안 | `analyze.audio` / `analyze.image` — **폐기** (스코프: 영상만) |

재생성: Excel·코드 변경 시 본 문서와 `ai-json.md`를 함께 갱신하세요.
