# 인프라 팀 가이드

> **범위:** AWS · S3 · RabbitMQ · Redis · EKS · HTTPS · 시크릿  
> **진입:** [../AGENTS.md](../AGENTS.md)

---

## 1. 필독 문서

1. [../architecture/system-overview.md](../architecture/system-overview.md)
2. [../integrations/s3.md](../integrations/s3.md)
3. [../integrations/rabbitmq.md](../integrations/rabbitmq.md)
4. [../requirements/index.md](../requirements/index.md) — `RQ-SEC-*`, `RQ-PER-*`, `RQ-NFR-*`
5. 기능명세서 Excel **INF** 시트

---

## 2. AWS 구성 (목표 아키텍처)

| 서비스 | 용도 | RQ |
| :--- | :--- | :--- |
| **S3** `forenshield-evidence` | 원본 WORM · copy · reports | RQ-SEC-150 |
| **S3** `forenshield-models` | AI 가중치 (Versioning) | — |
| **RDS PostgreSQL** | 애플리케이션 DB | RQ-NFR-* |
| **Amazon MQ / RabbitMQ** | BE ↔ AI 비동기 | RQ-REQ-049 |
| **EKS** | API · Worker Pod | RQ-PER-* |
| **VPC Endpoint** | S3 Private Link | RQ-SEC-150 |
| **IRSA** | Pod → S3 IAM (키 없음) | 보안 |

---

## 3. S3 정책 요약

| 경로 | Object Lock | 접근 |
| :--- | :--- | :--- |
| `original/` | Compliance WORM | BE Write once |
| `copy/` | 없음 | BE Write · AI Read |
| `reports/` | 없음 | BE Write · User Read |

디렉터리 구조: [s3.md §2](../integrations/s3.md)

---

## 4. RabbitMQ

| Exchange | Type | 용도 |
| :--- | :--- | :--- |
| `ai.analysis.exchange` | Topic | BE → AI |
| `ai.result.exchange` | Topic | AI → BE |
| `ai.dead.exchange` | Direct | DLQ |

- Persistent messages (delivery mode 2)
- Retry 3회 → DLX

→ [rabbitmq.md](../integrations/rabbitmq.md)

---

## 5. 네트워크·보안

| 요구 | 구현 |
| :--- | :--- |
| HTTPS only | ALB + TLS cert |
| 내부망 전용 | VPN / Private subnet |
| JWT secret | K8s Secret / Parameter Store |
| DB credentials | Secret Manager |
| `.env` | **Git 커밋 금지** |

---

## 6. 환경 변수 (BE 참고)

본 레포 `.env.example` 또는 README 참고. 대표 항목:

- `JWT_SECRET`
- `AWS_S3_EVIDENCE_BUCKET`
- `SPRING_RABBITMQ_*`
- `SPRING_DATASOURCE_*`

---

## 7. 블록체인 (로드맵)

RQ-SEC-151~152: 일 1회 Merkle Root → 스마트 컨트랙트 앵커.  
**운영 연동 전** BE·INF·법무 협의 필요.

---

## 8. 모니터링 (권장)

| 대상 | 지표 |
| :--- | :--- |
| API | latency p95, 5xx rate |
| RabbitMQ | queue depth, DLQ count |
| AI Worker | GPU util, job duration |
| S3 | 4xx/5xx, Object Lock deny |

---

## 9. PR·변경 시

- [ ] RQ-SEC / RQ-NFR / FN-INF-ID
- [ ] BE·AI 팀에 Exchange/버킷 변경 사전 공지
- [ ] `integrations/s3.md` · `rabbitmq.md` 갱신
