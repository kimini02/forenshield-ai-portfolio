# 블록체인 앵커링

> **RQ:** REQ-052 · DTL-078 · SEC-151~152

---

## 1. 앵커 타입

| 타입 | 트리거 | subjectHash |
| :--- | :--- | :--- |
| `EVIDENCE_HASH` | 증거 업로드 완료 | 원본 SHA-256 |
| `REPORT_HASH` | PDF 리포트 생성 | reportHash |
| `MERKLE_ROOT` | 매일 01:00 스케줄 | 전일 CoC `currentLogHash` Merkle Root |

---

## 2. API

| Method | Path |
| :--- | :--- |
| GET | `/api/v1/evidences/{evidenceId}/blockchain` |

증거 상세(`GET .../detail`) 응답 `blockchainInfo` 필드에 최신 원본 해시 앵커 요약 포함.

---

## 3. 설정 (`blockchain.anchor.*`)

| 키 | 기본 | 설명 |
| :--- | :--- | :--- |
| `enabled` | `true` | 앵커 비활성화 시 no-op |
| `mode` | `simulated` | `simulated` \| `http` |
| `network` | `local-simulated` | 체인 이름 (표시·저장) |
| `http-url` | — | INF 게이트워이 POST URL (`mode=http`) |
| `scheduler-enabled` | `true` | 일일 Merkle Job |
| `daily-cron` | `0 0 1 * * *` | Merkle 배치 cron |

환경 변수: `BLOCKCHAIN_ANCHOR_MODE`, `BLOCKCHAIN_ANCHOR_URL`, `BLOCKCHAIN_ANCHOR_NETWORK`

---

## 4. HTTP 게이트웨이 계약 (INF — Hyperledger Fabric 등)

**Request** `POST {http-url}`

```json
{
  "subjectHash": "abc...",
  "anchorType": "EVIDENCE_HASH",
  "network": "hyperledger-fabric-forenshield",
  "clientId": "forenshield-be",
  "evidenceId": 123,
  "reportId": null,
  "merkleBatchDate": "2026-06-22",
  "merkleLeafCount": 42
}
```

`evidenceId` / `reportId` / `merkleBatchDate` / `merkleLeafCount`는 타입에 따라 선택 필드.

선택 헤더: `X-Api-Key` (`blockchain.anchor.http-api-key`)

**Response**

```json
{
  "transactionHash": "fabric-tx-id",
  "blockNumber": 12345
}
```

INF 가이드: [`Infra/md/21.fabric-gateway-quickstart.md`](../../../../Infra/md/21.fabric-gateway-quickstart.md) · [`20.hyperledger-fabric-apply.md`](../../../../Infra/md/20.hyperledger-fabric-apply.md)

---

## 5. DB

테이블: `blockchain_anchors`  
마이그레이션: `db/schema/002_blockchain_anchors_and_reports.postgresql.sql`
