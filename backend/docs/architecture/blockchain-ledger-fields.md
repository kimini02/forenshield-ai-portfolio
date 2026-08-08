# 블록체인 원장(Ledger) 확장 필드 설계

> **작성일:** 2026-07-03  
> **상태:** 구현됨 (2026-07-04) — 정책 §8.2 확정 반영  
> **관련 문서:** [blockchain-implementation.md](./blockchain-implementation.md) · [integrations/blockchain.md](../integrations/blockchain.md) · [COC_LOG_PROGRESS.md](../COC_LOG_PROGRESS.md)

---

## 1. 목적

현재 ForenShield 블록체인 앵커는 **해시 1개(`subjectHash`) + 메타데이터 일부**만 Fabric 원장에 기록한다.

이 문서는 아래를 정의한다.

1. **원장에 추가할 필드** — 전자서명·인증서 지문·검증 결과·오프체인 상세 로그 해시·오프체인 위치 참조
2. **각 필드의 RDS/S3 출처** — 어디서 읽고, 어떻게 계산하는지
3. **블록체인·백엔드·INF에서 무엇을 바꿔야 하는지** — 구현 순서

**원칙:** 원본 파일·대용량 JSON·X.509 PEM 전체는 **온체인에 넣지 않는다.**  
원장에는 **지문(fingerprint)·서명·검증 결과·포인터**만 남긴다.

---

## 2. 현재 구현 요약 (As-Is)

### 2.1 앵커 타입 3종


| 타입              | 트리거              | `subjectHash`                         | 비고          |
| --------------- | ---------------- | ------------------------------------- | ----------- |
| `EVIDENCE_HASH` | 증거 업로드           | `evidences.original_hash_value`       | 원본 SHA-256  |
| `REPORT_HASH`   | PDF 리포트 생성       | `reports.report_hash`                 | PDF SHA-256 |
| `MERKLE_ROOT`   | 매일 01:00 / Admin | 전일 CoC `current_log_hash` Merkle Root | 일괄 앵커       |


### 2.2 Fabric Chaincode (`Infra/fabric/chaincode/anchor/anchor.go`)

현재 `AnchorRecord` 필드:

```json
{
  "subjectHash": "...",
  "anchorType": "EVIDENCE_HASH",
  "clientId": "forenshield-be",
  "evidenceId": "123",
  "reportId": "",
  "merkleBatchDate": "",
  "merkleLeafCount": "",
  "anchoredAt": "2026-07-03T01:00:00Z",
  "txId": "..."
}
```

**서명·매니페스트·오프체인 참조는 아직 없음.**

### 2.3 이미 오프체인(RDS/S3)에 있는 데이터


| 영역       | 테이블/스토리지             | 핵심 컬럼                                                                        |
| -------- | -------------------- | ---------------------------------------------------------------------------- |
| 증거 매니페스트 | `evidence_manifests` | `manifest_hash`, `manifest_json`, `signature_value`, `manifest_storage_path` |
| 증거 원본    | `evidences` + S3     | `original_hash_value`, `original_storage_path`                               |
| 리포트      | `reports` + S3       | `report_hash`, `storage_path`                                                |
| CoC 로그   | `custody_logs`       | `current_log_hash`, `event_payload_json`, `subject_hash`                     |
| 앵커 메타    | `blockchain_anchors` | `subject_hash`, `transaction_hash`, `status`                                 |


---

## 3. 설계 원칙 — 온체인 vs 오프체인

```
┌─────────────────────────────────────────────────────────────┐
│  온체인 (Fabric Ledger)                                      │
│  · subjectHash (앵커 대상 지문)                               │
│  · signature (전자서명 값)                                    │
│  · signerCertHash (서명 인증서 SHA-256)                       │
│  · certVerified (앵커 시점 서명 검증 결과)                    │
│  · offchainLogHash (상세 로그/JSON 덩어리 SHA-256)            │
│  · offchainRef (상세 데이터 위치 포인터, 내용 없음)           │
│  · anchorType, evidenceId, reportId, merkleBatchDate, ...     │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ 검증 시 offchainRef로 조회
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  오프체인 (RDS / S3)                                         │
│  · manifest_json, signature_value (전체)                      │
│  · X.509 인증서 PEM (Secrets Manager / 서명 모듈)             │
│  · custody_logs.event_payload_json (이벤트별 상세)            │
│  · 원본·복사본·PDF 바이트                                     │
└─────────────────────────────────────────────────────────────┘
```


| 넣지 않는 것                     | 이유                                     |
| --------------------------- | -------------------------------------- |
| 영상·PDF 원문                   | 용량·프라이버시·가스/TX 크기                      |
| X.509 PEM 전체                | 크기 큼 → `signerCertHash`로 지문만           |
| CoC `event_payload_json` 전체 | 건별·누적 크기 큼 → `offchainLogHash`로 묶음 지문만 |
| `manifest_json` 전체          | 이미 `manifest_hash`로 대표                 |


---

## 4. 원장 필드 정의

### 4.1 필드 카탈로그


| 원장 필드             | 타입                | 의미                                                            | 주요 출처 (RDS/S3)                                |
| ----------------- | ----------------- | ------------------------------------------------------------- | --------------------------------------------- |
| `subjectHash`     | `string` (64 hex) | **이 앵커가 고정하는 대상의 SHA-256**                                    | 타입별 상이 (§5)                                   |
| `signature`       | `string`          | 매니페스트(또는 앵커 대상)에 대한 **전자서명 값**                                | `evidence_manifests.signature_value`          |
| `signerCertHash`  | `string` (64 hex) | 서명에 사용한 **X.509 인증서 PEM의 SHA-256**                            | 서명 시 사용한 cert PEM → BE에서 계산 (DB 컬럼 **신규 권장**) |
| `certVerified`    | `boolean`         | 앵커 시점 `EvidenceManifestService.isSignatureValid(manifest)` 결과 | BE 검증 로직                                      |
| `offchainLogHash` | `string` (64 hex) | 원장에 넣지 않는 **상세 로그/JSON 묶음**의 SHA-256                          | 타입별 상이 (§5.4)                                 |
| `offchainRef`     | `object`          | 상세 데이터 **위치 포인터** (내용 없음)                                     | §4.2                                          |


기존 메타 필드(`anchorType`, `clientId`, `evidenceId`, `reportId`, `merkleBatchDate`, `merkleLeafCount`, `anchoredAt`, `txId`)는 **유지**.

### 4.2 `offchainRef` 구조

```json
{
  "manifestStoragePath": "cases/{caseKey}/{evidenceId}/manifest/manifest.json",
  "originalStoragePath": "cases/{caseKey}/{evidenceId}/original/{fileName}",
  "reportStoragePath": "cases/.../reports/{reportId}.pdf",
  "custodyLogBundleRef": "rds:custody_logs?batchDate=2026-07-02"
}
```


| 키                     | 출처                                         | 비고                                    |
| --------------------- | ------------------------------------------ | ------------------------------------- |
| `manifestStoragePath` | `evidence_manifests.manifest_storage_path` | S3 object key                         |
| `originalStoragePath` | `evidences.original_storage_path`          | S3 object key                         |
| `reportStoragePath`   | `reports.storage_path`                     | `REPORT_HASH` 앵커 시                    |
| `custodyLogBundleRef` | 논리적 참조                                     | `MERKLE_ROOT` / CoC 번들 앵커 시, RDS 조회 키 |


**규칙:** 값이 없는 키는 JSON에서 **생략** (빈 문자열 남발 금지).

### 4.3 `signerCertHash` 계산 (신규)

서명 시 사용하는 인증서 PEM을 정규화한 뒤 SHA-256:

```text
signerCertHash = SHA-256( UTF-8( normalizePem(certificatePem) ) )
```

- `normalizePem`: 줄바꿈 `\n` 통일, 앞뒤 공백 제거
- **저장:** `evidence_manifests`에 `signer_certificate_hash VARCHAR(64)` 컬럼 추가 권장 (앵커 시점 스냅샷)
- 앵커 시점 cert와 나중 검증 cert가 다를 수 있으므로 **앵커 당시 해시를 원장에 고정**

### 4.4 `certVerified` 의미


| 값       | 조건                                                                   |
| ------- | -------------------------------------------------------------------- |
| `true`  | `signature_status == SIGNED` 이고 `isSignatureValid(manifest) == true` |
| `false` | 서명 없음, 검증 실패, `FAILED`/`UNSIGNED`                                    |


**주의:** `false`여도 앵커 TX는 남길 수 있음 (감사: “당시 서명 검증 실패 상태를 기록”).  
다만 운영 정책상 `certVerified=false`면 앵커 **거부**할지는 별도 정책 결정 (§8.2).

---

## 5. `subjectHash` / `offchainLogHash` — 앵커 타입별 매핑

### 5.1 `EVIDENCE_HASH` (기존 + 확장 메타)


| 필드                                | 값                                          |
| --------------------------------- | ------------------------------------------ |
| `subjectHash`                     | `evidences.original_hash_value` (기존과 동일)   |
| `signature`                       | 해당 증거 `evidence_manifests.signature_value` |
| `signerCertHash`                  | 서명 cert PEM 해시 (§4.3)                      |
| `certVerified`                    | `isSignatureValid(manifest)`               |
| `offchainLogHash`                 | **증거 생성~업로드 CoC 묶음** SHA-256 (§5.4.1)      |
| `offchainRef.manifestStoragePath` | `evidence_manifests.manifest_storage_path` |
| `offchainRef.originalStoragePath` | `evidences.original_storage_path`          |


**트리거 시점:** 업로드 완료 + `ensureManifest()` 이후 (매니페스트·서명 준비 후).

### 5.2 `MANIFEST_HASH` (신규 타입 — 권장)

매니페스트만 별도로 고정하고 싶을 때.


| 필드                                | 값                                                                  |
| --------------------------------- | ------------------------------------------------------------------ |
| `subjectHash`                     | `evidence_manifests.manifest_hash`                                 |
| `signature`                       | `signature_value`                                                  |
| `signerCertHash`                  | §4.3                                                               |
| `certVerified`                    | `isSignatureValid(manifest)`                                       |
| `offchainLogHash`                 | `SHA-256( canonicalJson(manifest_json) )` — 보통 `manifest_hash`와 동일 |
| `offchainRef.manifestStoragePath` | `manifest_storage_path`                                            |


> **선택:** `EVIDENCE_HASH`에 매니페스트 필드를 붙이면 타입 추가 없이 가
>
> 능.  
> 법적 제출에서 “매니페스트 자체 앵커”가 필요하면 `MANIFEST_HASH` 분리.

### 5.3 `REPORT_HASH` (기존 + 확장 메타)


| 필드                                | 값                                         |
| --------------------------------- | ----------------------------------------- |
| `subjectHash`                     | `reports.report_hash` (기존)                |
| `signature`                       | (선택) 리포트 PDF에 대한 별도 서명 도입 시; **현재는 null** |
| `certVerified`                    | 리포트 서명 없으면 `null` 또는 생략                   |
| `offchainLogHash`                 | 리포트 생성 관련 CoC + 리포트 메타 JSON 묶음 SHA-256    |
| `offchainRef.reportStoragePath`   | `reports.storage_path`                    |
| `offchainRef.originalStoragePath` | 연결 `evidence_id`의 원본 경로                   |


### 5.4 `MERKLE_ROOT` (기존 + 확장 메타)


| 필드                                | 값                                                               |
| --------------------------------- | --------------------------------------------------------------- |
| `subjectHash`                     | 전일 CoC `current_log_hash` 목록의 Merkle Root (기존 `MerkleTreeUtil`) |
| `signature`                       | (선택) 플랫폼이 Root에 대한 서명을 붙일 경우                                    |
| `offchainLogHash`                 | **§5.4.2 custody 로그 JSON 묶음 해시** (신규)                           |
| `offchainRef.custodyLogBundleRef` | `rds:custody_logs?batchDate={date}`                             |


#### 5.4.1 증거 단위 CoC 묶음 해시 (`offchainLogHash` for EVIDENCE_HASH)

대상: 해당 `evidenceId`에 연결된 CoC 레코드 (targetType=`EVIDENCE` 또는 연관 `ANALYSIS_REQUEST`).

```text
1. custody_logs 조회 (evidenceId 기준, created_at ASC)
2. 각 row → canonical JSON 객체 (logId, actionType, subjectHash, currentLogHash, eventPayloadJson 파싱 결과, createdAt)
3. 배열을 JSON 직렬화 (키 정렬, UTF-8)
4. offchainLogHash = SHA-256( bytes )
```

#### 5.4.2 일별 CoC JSON 묶음 해시 (`offchainLogHash` for MERKLE_ROOT)

Merkle leaf는 `current_log_hash`만 쓰지만, **상세 증명**을 위해 당일 로그 전체 JSON의 해시를 추가:

```text
1. batchDate 00:00 ~ 24:00 custody_logs 조회 (created_at)
2. Merkle leaf 목록과 동일 정렬 기준으로 canonical JSON 배열 생성
3. offchainLogHash = SHA-256( canonicalJsonArray )
```

**역할 분담:**


| 해시                          | 역할                                             |
| --------------------------- | ---------------------------------------------- |
| `subjectHash` (Merkle Root) | 당일 CoC **무결성 요약** — 1 TX로 고정 (기존)              |
| `offchainLogHash`           | 당일 CoC **상세 내용 지문** — 원장에 본문 없이 “이 JSON 묶음” 증명 |


감사 시: RDS에서 당일 로그를 다시 직렬화 → `offchainLogHash` 대조 → Merkle leaf와 Root 재계산.

---

## 6. 확장된 원장 레코드 예시 (Fabric)

### 6.1 `EVIDENCE_HASH` 예시

```json
{
  "subjectHash": "a1b2c3...",
  "anchorType": "EVIDENCE_HASH",
  "clientId": "forenshield-be",
  "evidenceId": "91",
  "signature": "MEUCIQ...",
  "signerCertHash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "certVerified": true,
  "offchainLogHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "offchainRef": {
    "manifestStoragePath": "cases/rabbitmq_test2/91/manifest/manifest.json",
    "originalStoragePath": "cases/rabbitmq_test2/91/original/sample.mp4"
  },
  "anchoredAt": "2026-07-03T02:00:00Z",
  "txId": "abc123..."
}
```

### 6.2 `MERKLE_ROOT` 예시

```json
{
  "subjectHash": "merkle-root-hex...",
  "anchorType": "MERKLE_ROOT",
  "clientId": "forenshield-be",
  "merkleBatchDate": "2026-07-02",
  "merkleLeafCount": "128",
  "offchainLogHash": "daily-coc-bundle-hash...",
  "offchainRef": {
    "custodyLogBundleRef": "rds:custody_logs?batchDate=2026-07-02"
  },
  "certVerified": null,
  "anchoredAt": "2026-07-03T01:00:00Z",
  "txId": "def456..."
}
```

---

## 7. 블록체인(Fabric)에서 처리할 일

### 7.1 Chaincode 스키마 확장

**파일:** `Infra/fabric/chaincode/anchor/anchor.go`

`AnchorRecord`에 필드 추가:

```go
type AnchorRecord struct {
    SubjectHash      string          `json:"subjectHash"`
    AnchorType       string          `json:"anchorType"`
    ClientID         string          `json:"clientId"`
    EvidenceID       string          `json:"evidenceId,omitempty"`
    ReportID         string          `json:"reportId,omitempty"`
    MerkleBatchDate  string          `json:"merkleBatchDate,omitempty"`
    MerkleLeafCount  string          `json:"merkleLeafCount,omitempty"`
    Signature        string          `json:"signature,omitempty"`
    SignerCertHash   string          `json:"signerCertHash,omitempty"`
    CertVerified     *bool           `json:"certVerified,omitempty"`
    OffchainLogHash  string          `json:"offchainLogHash,omitempty"`
    OffchainRef      json.RawMessage `json:"offchainRef,omitempty"`
    AnchoredAt       string          `json:"anchoredAt"`
    TxID             string          `json:"txId,omitempty"`
}
```

`AnchorHash` 함수 인자 확장 또는 **JSON payload 1개**로 받도록 단순화 (권장: 게이트웨이가 JSON blob 전달).

### 7.2 Anchor Gateway (INF HTTP)

`POST {BLOCKCHAIN_ANCHOR_URL}` 요청 본문 확장:

```json
{
  "subjectHash": "...",
  "anchorType": "EVIDENCE_HASH",
  "network": "hyperledger-fabric-forenshield",
  "clientId": "forenshield-be",
  "evidenceId": 91,
  "signature": "...",
  "signerCertHash": "...",
  "certVerified": true,
  "offchainLogHash": "...",
  "offchainRef": {
    "manifestStoragePath": "...",
    "originalStoragePath": "..."
  }
}
```

게이트웨이 → Fabric `AnchorHash` invoke 시 **불변 레코드**로 저장.  
이미 존재하는 key(`EVIDENCE:{id}`)는 **덮어쓰지 않음** (현재 chaincode 동작 유지).

### 7.3 TX 크기 한도


| 항목                   | 예상 크기      | 비고        |
| -------------------- | ---------- | --------- |
| `signature` (Base64) | 수백~수 KB    | 허용        |
| `offchainRef`        | 수백 B       | 경로 문자열만   |
| PEM 전체               | 수 KB~수십 KB | **넣지 않음** |


Fabric endorsement payload 한도(설정별 ~~64KB~~) 내 유지.

---

## 8. 백엔드(BE)에서 처리할 일

### 8.1 구현 단계 (권장 순서)


| 단계  | 작업                                                                    | 산출물                            |
| --- | --------------------------------------------------------------------- | ------------------------------ |
| 1   | `signerCertHash` 계산 + `evidence_manifests.signer_certificate_hash` 컬럼 | DB migration `006_...sql`      |
| 2   | `OffchainLogHashService` — 증거/일별 CoC JSON canonical hash              | Java service + unit test       |
| 3   | `BlockchainAnchorRequest` / `HttpBlockchainAnchorClient` 필드 확장        | gateway JSON                   |
| 4   | `BlockchainAnchor` 엔티티·DTO에 스냅샷 컬럼 (선택)                               | 감사·재조회용 RDS 복제                 |
| 5   | `anchorEvidenceHash` — manifest 조회 후 확장 필드 채움                         | `BlockchainAnchorService`      |
| 6   | `anchorDailyMerkleRoot` — `offchainLogHash` + `offchainRef`           | Merkle 배치                      |
| 7   | (선택) `MANIFEST_HASH` 타입·`anchorManifestHash()`                        | enum + migration CHECK         |
| 8   | 무결성 API — 원장 `certVerified` vs 현재 재검증 비교                              | `IntegrityVerificationService` |


### 8.2 정책 결정 (확정)


| 질문                             | 결정 |
| ------------------------------ | ---- |
| `certVerified=false`일 때 앵커 허용? | **앵커 보류 + RDS `FAILED`** (`errorCode=MANIFEST_SIGNATURE_INVALID`, Fabric TX 없음). 업로드·S3·Evidence는 유지. |
| `MANIFEST_HASH` 별도 타입?         | **A) `EVIDENCE_HASH`에 통합** — manifest `signature` / `signerCertHash` / `certVerified` / `offchainRef.manifestStoragePath`를 동일 TX 메타로 기록. |
| `signature`를 `REPORT_HASH`에도?  | **PDF 서명 도입 전까지 생략** (`null` / 필드 생략). 도입 후 EVIDENCE와 동일 정책. |


**구현 포인트**

- `blockchain_anchors.status = FAILED`, `error_code = MANIFEST_SIGNATURE_INVALID`
- 무결성 API: `BLOCKCHAIN_HASH` = 원본 해시 OK / 앵커 없음(서명 실패), `BLOCKCHAIN_CERT` = 원장 `certVerified` vs 현재 재검증
- 재시도: `BlockchainAnchorService.retryEvidenceHashAnchor` (ANCHORED가 아니면 재시도 가능)

### 8.3 변경 파일


| 영역     | 파일                                                               |
| ------ | ---------------------------------------------------------------- |
| DTO    | `BlockchainAnchorRequest.java`, `BlockchainAnchorRecordDto.java`, `OffchainRef.java` |
| 서비스    | `BlockchainAnchorService.java`, `EvidenceManifestService.java`, `OffchainLogHashService.java`, `SignerCertificateHashCalculator.java` |
| HTTP   | `HttpBlockchainAnchorClient.java`                                |
| DB     | `blockchain_anchors` 스냅샷 컬럼, `evidence_manifests.signer_certificate_hash` |
| Fabric | `anchor.go`, `gateway/src/fabric.js`                             |


### 8.4 `blockchain_anchors` RDS 확장 (권장)

원장과 동일 스냅샷을 DB에도 저장 (체인 장애 시 API 응답용):

```sql
ALTER TABLE blockchain_anchors
    ADD COLUMN IF NOT EXISTS signature_value TEXT,
    ADD COLUMN IF NOT EXISTS signer_cert_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS cert_verified BOOLEAN,
    ADD COLUMN IF NOT EXISTS offchain_log_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS offchain_ref_json JSONB;
```

---

## 9. 검증·감사 흐름 (To-Be)

```
[사용자] 무결성 / 블록체인 탭 조회
    │
    ▼
[BE] blockchain_anchors + Fabric GetAnchor
    │
    ├─ subjectHash vs 현재 evidences.original_hash_value
    ├─ certVerified (원장) vs isSignatureValid (현재)
    ├─ signerCertHash vs 현재 서명 cert 해시
    └─ offchainLogHash vs RDS CoC JSON 재계산
            │
            ▼
    offchainRef → S3 manifest / RDS custody_logs 조회
            │
            ▼
    [UI] 항목별 PASS / FAIL / SKIPPED
```

---

## 10. 현재 코드와의 Gap


| 항목                 | 현재                    | 목표                                   |
| ------------------ | --------------------- | ------------------------------------ |
| 원장 `signature`     | 없음                    | `evidence_manifests.signature_value` |
| `signerCertHash`   | 없음 (cert PEM DB 미저장)  | 서명 시 계산·저장                           |
| `certVerified`     | 무결성 API에서만 사용         | 앵커 시점 스냅샷을 원장에도                      |
| `offchainLogHash`  | 없음                    | CoC JSON 묶음 SHA-256                  |
| `offchainRef`      | 없음                    | S3 key / RDS 배치 참조                   |
| `manifest_hash` 앵커 | 없음 (`EVIDENCE_HASH`만) | 통합 또는 `MANIFEST_HASH`                |
| Chaincode          | 7필드                   | §7.1 확장                              |


---

## 11. 구현 체크리스트

### INF / Fabric

- `AnchorRecord` 스키마 확장
- Anchor Gateway POST body 파싱·invoke 인자 반영
- Chaincode 버전 업·채널 upgrade 절차 문서화
- TX 크기·endorsement 정책 점검

### Backend

- `signer_certificate_hash` migration
- `OffchainLogHashService` + 테스트
- `BlockchainAnchorRequest` 확장
- `anchorEvidenceHash` / `anchorDailyMerkleRoot` 필드 채움
- `blockchain_anchors` 스냅샷 컬럼
- API 응답(`blockchainInfo`) 필드 추가

### Frontend (후속)

- 무결성/블록체인 UI에 `certVerified`, `offchainLogHash` 표시
- explorer + offchainRef 링크 (S3는 presigned)

---

## 12. 참고 — 필드명 대응


| 사용자 용어        | 문서 JSON 키                               | RDS                                  |
| ------------- | --------------------------------------- | ------------------------------------ |
| 전자서명 값        | `signature`                             | `evidence_manifests.signature_value` |
| 서명자 인증서 해시    | `signerCertHash`                        | (신규) `signer_certificate_hash`       |
| 서명 검증 결과      | `certVerified`                          | 런타임 `isSignatureValid()`             |
| 오프체인 상세 로그 해시 | `offchainLogHash`                       | 계산값                                  |
| 오프체인 위치 참조    | `offchainRef`                           | manifest / original / report paths   |
| 매니페스트 전체 지문   | `subjectHash` (MANIFEST) 또는 EVIDENCE 메타 | `manifest_hash`                      |


---

## 13. 변경 이력


| 날짜         | 내용                               |
| ---------- | -------------------------------- |
| 2026-07-03 | 초안 — 원장 확장 필드·매핑·BE/Fabric 구현 방향 |


