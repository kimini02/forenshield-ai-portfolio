# ForenShield 블록체인 구현 상세 설명

> **작성 기준:** 2026-06-19 · `main` 브랜치 코드  
> **관련 RQ:** REQ-052 · DTL-078~080 · SEC-151~152 · CMP-103  
> **짧은 API 요약:** [../integrations/blockchain.md](../integrations/blockchain.md)

---

## 1. 한 줄 요약

우리 시스템은 **파일 전체를 블록체인에 올리지 않고**, SHA-256 **해시만 외부 체인(또는 시뮬레이터)에 “앵커(기록)”** 하는 방식을 씁니다.

- 증거 원본 해시 → 업로드 직후 1건 앵커  
- PDF 리포트 해시 → 리포트 생성 직후 1건 앵커  
- 하루치 CoC(Chain of Custody) 로그 → **Merkle Root**로 묶어 1건 앵커  

내부 DB(`blockchain_anchors`)에 트랜잭션 ID·상태·해시를 남기고, 상세/무결성/비교 검증 API에서 **“등록 당시 해시 vs 현재 해시”** 를 대조합니다.

---

## 2. 왜 “해시 앵커링” 기법을 썼는가

### 2.1 선택한 기법

| 기법 | 설명 |
| :--- | :--- |
| **Hash Anchoring** | 파일·리포트·로그 묶음의 SHA-256만 블록체인에 기록 |
| **Merkle Tree 배치 앵커** | 많은 CoC 이벤트를 Merkle Root 하나로 압축 후 1 TX에 앵커 |
| **Strategy 패턴 클라이언트** | `simulated` / `http` 두 가지 앵커 전송 구현을 설정으로 교체 |
| **이중 기록 (DB + Chain)** | `blockchain_anchors` 테이블 + (운영 시) 실제 TX 해시 |

### 2.2 왜 이 방식인가 (요구·제약)

1. **증거 파일은 용량이 크고 개인정보·수사 정보가 포함** → 온체인 저장은 비현실적  
2. **포렌식 표준은 “원본 해시 고정 + 변경 불가 기록”** → SHA-256 앵커면 법적·기술적 요구 충족  
3. **가스/트랜잭션 비용** → CoC 로그가 많으면 건별 앵커는 비용·지연 폭증 → **Merkle Root 일괄 앵커**  
4. **체인 종류·노드·키 관리는 INF 담당** → BE는 HTTP 게이트웨이만 호출 (`HttpBlockchainAnchorClient`)  
5. **로컬·CI·데모** → 실제 체인 없이도 플로우 검증 (`SimulatedBlockchainAnchorClient`)

### 2.3 장점 (이 기법을 택한 이유)

| 장점 | 내용 |
| :--- | :--- |
| **비용·성능** | 64자 hex 해시만 전송, 대용량 미디어 미포함 |
| **프라이버시** | 원본 영상·메타가 공개 체인에 노출되지 않음 |
| **검증 가능** | TX 시각·해시가 제3자(공개 ledger)에 남아 **사후 부인 방지** |
| **확장성** | INF가 Polygon·Hyperledger 등 체인만 바꿔도 BE 코드 변경 최소 |
| **방어 깊이** | CoC 해시 체인(내부) + 블록체인 앵커(외부) 이중 구조 |
| **운영 유연** | `enabled=false`로 전체 비활성, `scheduler-enabled=false`로 Merkle만 끔 |

---

## 3. 전체 아키텍처

```
[업로드] FileService
    └─ SHA-256(original) ──► BlockchainAnchorService.anchorEvidenceHash()
                                    │
[PDF 생성] ReportPdfService         │
    └─ SHA-256(pdf) ──────► anchorReportHash()
                                    │
[매일 01:00 / Admin API]            │
    └─ CoC currentLogHash[]         │
         └─ MerkleTreeUtil ──► anchorDailyMerkleRoot()
                                    │
                                    ▼
                         executeAnchor()  ──► blockchain_anchors (PENDING)
                                    │
                                    ▼
                         BlockchainAnchorClient.anchor()
                           ├─ SimulatedBlockchainAnchorClient (mode=simulated)
                           └─ HttpBlockchainAnchorClient (mode=http → INF)
                                    │
                                    ▼
                         status: ANCHORED | FAILED + transactionHash

[조회·검증]
    ├─ GET .../blockchain          → 앵커 목록·Merkle 요약
    ├─ GET .../detail.blockchainInfo → hashValid, explorer URL
    ├─ GET .../integrity/verify    → BLOCKCHAIN_HASH 항목
    └─ POST .../compare/verify     → BLOCKCHAIN_HASH 대조 항목
```

---

## 4. 앵커 타입 3종

구현: `BlockchainAnchorType` · `BlockchainAnchorService`

| 타입 | 트리거 | subjectHash | 연관 ID |
| :--- | :--- | :--- | :--- |
| `EVIDENCE_HASH` | 증거 업로드 완료 (`FileService.upload`) | 원본 SHA-256 | `evidence_id` |
| `REPORT_HASH` | 분석/비교 PDF 저장 (`ReportPdfService`) | PDF SHA-256 | `report_id`, `evidence_id` |
| `MERKLE_ROOT` | 스케줄(매일 01:00) 또는 Admin 수동 | 전일 CoC Merkle Root | `merkle_batch_date`, `merkle_leaf_count` |

### 4.1 EVIDENCE_HASH — 증거 원본 앵커

**흐름**

1. `FileService`가 파일 저장·SHA-256 계산·S3 업로드·CoC `UPLOADED` 기록  
2. `blockchainAnchorService.anchorEvidenceHash(savedEvidence, uploaderId)` 호출  
3. 이미 `ANCHORED`인 동일 증거 앵커가 있으면 **재앵커하지 않음** (멱등)

**왜 업로드 직후인가**

- 수집 시점의 원본 해시를 **가장 이른 시각**에 외부에 고정  
- 이후 DB·스토리지 변조 시 `hashValid=false`로 탐지 가능  

**코드 위치:** `FileService.java` (upload 마지막), `BlockchainAnchorService.anchorEvidenceHash()`

### 4.2 REPORT_HASH — PDF 리포트 앵커

**흐름**

1. `ReportPdfService`가 PDF 바이트 생성 → `reportHash = SHA-256(pdf)`  
2. `reports` 테이블 저장 후 `anchorReportHash(saved, userId)`  
3. 리포트별 1회 앵커 (이미 ANCHORED면 스킵)

**왜 리포트도 앵커하는가**

- 분석 결과 PDF는 **법적 제출물**에 가까움  
- PDF 변조 시 등록 해시와 불일치 → 무결성 API·감사 추적  

**코드 위치:** `ReportPdfService.java` (`persistAnalysisReport`, `persistCompareReport`)

### 4.3 MERKLE_ROOT — CoC 일괄 앵커

**흐름**

1. 대상 일자(`batchDate`, 기본: **어제**)의 CoC 로그 수집  
2. 각 로그의 `currentLogHash`를 leaf로 사용 (중복 제거)  
3. `MerkleTreeUtil.computeRoot()` → Root 해시  
4. Root를 `MERKLE_ROOT` 타입으로 1 TX 앵커  
5. 동일 `merkle_batch_date`에 이미 앵커 있으면 **스킵**

**트리거**

- **자동:** `BlockchainAnchorScheduler` — cron `0 0 1 * * *` (매일 01:00)  
- **수동:** `POST /api/v1/admin/blockchain/merkle/anchor?batchDate=` (ADMIN)

**코드 위치:** `BlockchainAnchorService.anchorDailyMerkleRoot()`, `AdminBlockchainController`, `BlockchainAnchorScheduler`

---

## 5. Merkle Tree 구현 (기법 상세)

**파일:** `MerkleTreeUtil.java`

### 5.1 알고리즘

1. leaf 해시 목록을 **소문자 정규화** 후 **사전순 정렬**  
2. 인접 두 leaf를 이어 붙인 문자열을 SHA-256 → 상위 노드  
3. leaf 개수가 홀수면 **마지막 leaf를 한 번 더 사용** (duplicate sibling)  
4. 레벨이 1개가 될 때까지 반복 → Root  

```java
// 개념 요약 (실제 코드와 동일)
while (level.size() > 1) {
    nextLevel.add(SHA256(left + right));  // 홀수면 right = left
}
```

### 5.2 왜 Merkle Tree인가

| 대안 | 문제 | Merkle 선택 이유 |
| :--- | :--- | :--- |
| CoC 로그 **건별** 블록체인 TX | TX 수·비용 폭증 | **하루 1 TX**로 수백~수천 이벤트 커버 |
| Root 없이 “마지막 CoC 해시”만 앵커 | 중간 이벤트 개별 증명 어려움 | leaf 포함 시 **포함 증명(inclusion proof)** 확장 가능 (현재 BE는 Root만 앵커) |
| DB만 신뢰 | 운영자 DB 변조 시 외부 근거 없음 | Root가 **제3자 ledger**에 남음 |

### 5.3 CoC와의 관계

CoC 자체는 **블록체인이 아니라 DB 해시 체인**입니다 (`CustodyLogService`).

- 각 CoC 레코드: `currentLogHash = SHA256(previousLogHash | actor | action | ...)`  
- Merkle은 **그날 생성된 CoC leaf들의 집합**을 외부에 한 번 더 고정하는 **2차 방어층**

**CoC 체인 코드:** `CustodyLogService.buildHashInput()`, `verifyChainIntegrity()`

---

## 6. 블록체인 클라이언트 (체인 연동 추상화)

**인터페이스:** `BlockchainAnchorClient`  
**구현 2종 (Spring `@ConditionalOnProperty`로 택1)**

### 6.1 SimulatedBlockchainAnchorClient (기본)

- **설정:** `blockchain.anchor.mode=simulated` (기본값)  
- **동작:** `SHA256("simulated-anchor|" + anchorType + "|" + subjectHash)` → `0x...` 형태 TX 해시 생성  
- **blockNumber:** 항상 `1`  

**왜 썼는가**

- 로컬·H2·CI에서 **INF/실체인 없이** 전체 플로우·테스트 가능  
- `FeatureApiControllerTest`, `BlockchainAnchorServiceTest` 등에서 검증  

### 6.2 HttpBlockchainAnchorClient (운영)

- **설정:** `blockchain.anchor.mode=http`, `blockchain.anchor.http-url={INF 게이트웨이}`  
- **동작:** POST JSON `{ subjectHash, anchorType }` → `{ transactionHash, blockNumber }`  

**왜 HTTP 게이트웨이인가**

- **관심사 분리:** BE는 “무엇을 앵커할지”, INF는 “어느 체인·지갑·가스”  
- 체인 교체·스마트컨트랙트 변경 시 **BE 재배포 불필요**  
- 실패 시 `FAILED` + `errorMessage` 저장 → 운영 모니터링  

**계약 문서:** [../integrations/blockchain.md](../integrations/blockchain.md) §4

---

## 7. 앵커 실행 파이프라인 (`executeAnchor`)

**파일:** `BlockchainAnchorService.executeAnchor()`

```
1. blockchain_anchors INSERT (status=PENDING)
2. anchorClient.anchor(subjectHash, anchorType)
3. 성공 → ANCHORED, transactionHash, blockNumber, anchoredAt
   실패 → FAILED, errorMessage
4. (증거/리포트만) notifyBlockchainAnchored 알림
5. UPDATE 저장
```

### 7.1 상태 머신

| status | 의미 |
| :--- | :--- |
| `PENDING` | DB 저장 직후, 클라이언트 호출 전/중 |
| `ANCHORED` | TX 해시 수신 완료 |
| `FAILED` | 게이트웨이 오류·설정 오류 |

### 7.2 멱등·중복 방지

- `EVIDENCE_HASH` / `REPORT_HASH`: 동일 대상에 **이미 ANCHORED** 있으면 새 TX 없음  
- `MERKLE_ROOT`: `merkle_batch_date` + 타입으로 **일 1회**  

**왜:** 재업로드·PDF 재생성·스케줄 중복 실행 시 **불필요 TX·DB 중복** 방지

---

## 8. DB 스키마

**테이블:** `blockchain_anchors`  
**마이그레이션:** `src/main/resources/db/schema/002_blockchain_anchors_and_reports.postgresql.sql`  
**엔티티:** `BlockchainAnchor.java`

| 컬럼 | 용도 |
| :--- | :--- |
| `anchor_type` | EVIDENCE_HASH / REPORT_HASH / MERKLE_ROOT |
| `subject_hash` | 앵커된 SHA-256 (또는 Merkle Root) |
| `transaction_hash` | 체인 TX ID (simulated면 0x...) |
| `block_number` | 블록 높이 |
| `network` | `local-simulated` 등 표시용 |
| `merkle_batch_date`, `merkle_leaf_count` | Merkle 배치 메타 |
| `status`, `error_message` | 결과 |
| `evidence_id`, `report_id`, `created_by` | 연관 |

**왜 DB에도 남기는가**

- FE API·감사 로그·Admin 화면은 **DB 조회가 빠름**  
- 체인 익스플로러 장애 시에도 **내부 기록** 유지  
- PENDING/FAILED 추적으로 **재시도·장애 대응** 가능  

---

## 9. API·검증 연동

### 9.1 조회 API

| API | 역할 |
| :--- | :--- |
| `GET /api/v1/evidences/{id}/blockchain` | 증거·리포트·최신 Merkle 앵커 목록 |
| `GET /api/v1/evidences/{id}/detail` → `blockchainInfo` | 상세 UI용 요약 + `hashValid` |

**BlockchainInfoDto 필드**

- `hashValid`: 앵커 `subject_hash` == 현재 `evidence.originalHashValue`  
- `verificationMessage`: 사용자 메시지  
- `transactionExplorerUrl`: `explorer-url-template`의 `{txHash}` 치환 (INF 설정 전이면 null)

### 9.2 무결성 검증 (SEC-153)

**파일:** `IntegrityVerificationService.checkBlockchainHash()`

- 앵커 없음 → **valid=true** (경고 메시지: “앵커링된 기록 없음”)  
- 앵커 있음 + 해시 불일치 → **valid=false**, `BLOCKCHAIN_HASH_MISMATCH`, `SECURITY_ALERT` 알림  

**왜 앵커 없을 때 valid=true인가**

- 블록체인은 **선택적/단계적 도입** (INF URL 대기)  
- 서명·CoC 실패는 더 치명적 → 블록체인 미구축 시 **전체 상세 조회 차단 방지**

### 9.3 비교 검증 (CMP-103)

**파일:** `CompareVerificationService` — `BLOCKCHAIN_HASH` 항목

- `originalValue`: `blockchain_anchors.subject_hash` (ANCHORED)  
- `candidateValue`: 업로드한 대조 파일 SHA-256  
- 둘 다 있으면 MATCH/MISMATCH, 없으면 SKIPPED  

**왜 compare에 넣었는가**

- “블록체인에 등록된 공식 해시”와 “지금 비교하는 파일”을 **한 화면에서 대조** (RQ-CMP-103)

---

## 10. 설정 (`application.yaml`)

```yaml
blockchain:
  anchor:
    enabled: true                    # false면 모든 앵커 no-op
    mode: simulated                  # simulated | http
    network: local-simulated
    http-url:                        # mode=http 일 때 INF URL
    scheduler-enabled: true
    daily-cron: "0 0 1 * * *"       # Merkle 배치
    explorer-url-template:           # e.g. https://polygonscan.com/tx/{txHash}
```

**환경 변수:** `BLOCKCHAIN_ANCHOR_ENABLED`, `BLOCKCHAIN_ANCHOR_MODE`, `BLOCKCHAIN_ANCHOR_URL`, `BLOCKCHAIN_EXPLORER_URL_TEMPLATE`

**Properties 클래스:** `BlockchainAnchorProperties.java`

---

## 11. 주요 소스 파일 맵

| 역할 | 경로 |
| :--- | :--- |
| 앵커 오케스트레이션 | `service/BlockchainAnchorService.java` |
| Merkle Root 계산 | `util/MerkleTreeUtil.java` |
| 시뮬레이터 클라이언트 | `service/blockchain/client/SimulatedBlockchainAnchorClient.java` |
| HTTP 게이트웨이 클라이언트 | `service/blockchain/client/HttpBlockchainAnchorClient.java` |
| 일일 스케줄 | `scheduler/BlockchainAnchorScheduler.java` |
| Admin 수동 Merkle | `controller/AdminBlockchainController.java` |
| 업로드 시 증거 앵커 | `service/FileService.java` |
| PDF 리포트 앵커 | `service/ReportPdfService.java` |
| 상세 blockchainInfo | `service/EvidenceDetailService.java` |
| 무결성 BLOCKCHAIN 검사 | `service/IntegrityVerificationService.java` |
| 비교 BLOCKCHAIN_HASH | `service/CompareVerificationService.java` |
| CoC 해시 체인 (Merkle leaf 원천) | `service/CustodyLogService.java` |
| DB 엔티티 | `domain/BlockchainAnchor.java` |
| 테스트 | `test/.../BlockchainAnchorServiceTest.java`, `util/MerkleTreeUtilTest.java` |

---

## 12. 우리가 쓰지 **않은** 것 (의도적 제외)

| 기법 | 제외 이유 |
| :--- | :--- |
| **온체인 파일 저장** | 용량·비용·프라이버시 |
| **스마트컨트랙트 직접 호출 (BE)** | INF 게이트웨이에 위임 |
| **Private key / 지갑 관리 (BE)** | 보안·운영 INF 영역 |
| **Proof of Work 채굴** | 퍼블릭 PoW와 무관, enterprise anchoring만 필요 |
| **CoC 전체를 건별 온체인** | Merkle로 비용 절감 |

---

## 13. 현재 한계·INF 협업 포인트

- **운영 체인:** `mode=http` + `http-url` + `explorer-url-template`은 INF 배포 후 활성  
- **Merkle inclusion proof:** Root만 앵커, leaf별 증명 API는 미구현 (필요 시 확장)  
- **실패 재시도:** FAILED 레코드 자동 재앵커 Job 없음 (수동/향후 작업)  
- **Merkle leaf 수집:** `custodyLogRepository.findAll()` 후 메모리 필터 — 대규모 시 쿼리 최적화 여지  

---

## 14. 기법 선택 요약 (발표·리뷰용)

1. **Hash Anchoring** — 포렌식 표준에 맞고, 파일은 off-chain 유지  
2. **Merkle Batch Anchoring** — CoC 대량 이벤트를 1 TX로 외부 고정, 비용·지연 절감  
3. **Internal CoC Hash Chain + External Anchor** — DB 변조·외부 부인 모두에 대응하는 이중 구조  
4. **Simulated / HTTP Strategy** — 개발 속도와 운영 체인 분리  
5. **DB Audit + Chain TX** — API·감사·장애 대응을 위한 운영 가능한 이중 기록  
6. **hashValid / BLOCKCHAIN_HASH 검증** — 앵커가 “등록용 장식”이 아니라 **실시간 무결성 검사**에 연결  

---

## 15. 변경 이력

| 날짜 | 내용 |
| :--- | :--- |
| 2026-06-19 | 초판 — main 기준 구현·기법·선택 이유 정리 |
