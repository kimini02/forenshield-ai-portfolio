# ForenShield 백엔드 테스트 매트릭스 (Tier 1 · Tier 2)

> **작성일:** 2026-07-07  
> **기준:** 멘토 테스트 전략 가이드 §3.2·§3.5 · `requirements/traceability.md` · 실제 테스트 코드  
> **용도:** Google Sheet / Notion 복사 · 발표 자료 · SK-469 E2E 추적

---

## 사용 방법

| 열 | 설명 |
| :--- | :--- |
| **TC-ID** | 테스트 케이스 고유 ID |
| **Tier** | 1 = 필수(발표·고객 대응) · 2 = 여유 시 |
| **RQ-ID** | 요구사항 |
| **FN-BE-ID** | 백엔드 기능 ID (`traceability.md`) |
| **대상 API/컴포넌트** | 검증 대상 |
| **케이스 요약** | 무엇을 검증하는가 |
| **테스트 (자동)** | `클래스.메서드` 또는 `./gradlew test --tests` |
| **유형** | Unit · Integration · Contract · Manual |
| **Expected** | 기대 결과 |
| **Actual** | *(실행 시 기록)* |
| **결과** | Pass / Fail / Skip |
| **작성** | 케이스 작성자 |
| **실행** | *(크로스 검증 권장 — 작성자 ≠ 실행자)* |

**전체 자동 회귀:**

```powershell
cd c:\Final_Project\backend-forensic
.\gradlew test
```

**Tier 1만 실행 (대표 클래스):**

```powershell
.\gradlew test --tests "com.example.demo.controller.AuthControllerTest" `
  --tests "com.example.demo.controller.EvidenceControllerTest" `
  --tests "com.example.demo.controller.Sprint45E2EIntegrationTest" `
  --tests "com.example.demo.service.analysis.AnalysisAiResultIntegrationTest"
```

---

## 통합 시나리오 (Tier 1 — 10개 압축)

| 시나리오 ID | 흐름 | 연결 RQ | 자동/수동 | 대응 TC |
| :--- | :--- | :--- | :---: | :--- |
| **S-01** | 로그인 → JWT | LOGIN-020~021 | 자동 | TC-T1-001~004 |
| **S-02** | MP4 업로드 → hashValue | REQ-047~048 | 자동 | TC-T1-010~015 |
| **S-03** | analyze → local worker → COMPLETED | REQ-049 | 자동 | TC-T1-020~022 |
| **S-04** | AI JSON → DB → detail | REQ-049, DTL-053~057 | 자동 | TC-T1-023~025, S-45 |
| **S-05** | 업로드 → CoC 3건 → 체인 검증 | REQ-051 | 자동 | TC-T1-030~032 |
| **S-06** | 상세 → PDF → REPORT CoC | DTL-084~087 | 자동 | TC-T1-040~041 |
| **S-07** | 일반 사용자 → admin API 403 | NFR-162 | 자동 | TC-T1-050 |
| **S-08** | 무결성 실패 → SECURITY_ALERT | SEC-153 | 자동 | TC-T1-060~062 |
| **S-09** | 업로드→해시→AI→CoC→상세 **전체** | SK-469 | **수동 E2E** | TC-T1-M01 |
| **S-10** | 에러 JSON `errorCode` 계약 | COM-* | 자동 | TC-T1-070 |

---

# Tier 1 — 필수 (발표 · Code Freeze 보증)

> **보증 선언:** 핵심 수사 여정(로그인·업로드·분석·CoC·상세·PDF·권한)은 Tier 1 자동 테스트로 검증.  
> AI GPU 실연동·S3 WORM·블록체인 http는 Tier 2·수동(UAT) 범위.

## 1. 인증 · 권한

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T1-001 | RQ-LOGIN-020 | FN-LOGIN-020-BE | `POST /api/auth/login` | 승인된 일반 사용자 로그인 | `AuthControllerTest.loginSuccessAsUser` | Integration | 200 + `accessToken` |
| TC-T1-002 | RQ-LOGIN-020 | FN-LOGIN-020-BE | `POST /api/auth/login` | 승인된 관리자 로그인 | `AuthControllerTest.loginSuccessAsAdmin` | Integration | 200 + `role=ROLE_ADMIN` |
| TC-T1-003 | RQ-LOGIN-021 | FN-LOGIN-021-BE | `POST /api/auth/login` | 비밀번호 불일치 | `AuthControllerTest.loginFailsWithWrongPassword` | Integration | 401 + `INVALID_CREDENTIALS` |
| TC-T1-004 | RQ-LOGIN-021 | FN-LOGIN-021-BE | `POST /api/auth/login` | PENDING 계정 | `AuthControllerTest.loginFailsWhenPending` | Integration | 401 + `ACCOUNT_PENDING` |
| TC-T1-005 | RQ-NFR-160 | FN-NFR-160-BE | 증거 API | JWT 없이 호출 | `EvidenceControllerTest.shouldRejectUnauthorizedUpload` | Integration | 401 |
| TC-T1-050 | RQ-NFR-162 | FN-NFR-162-BE | `/api/v1/admin/**` | 일반 사용자 admin 접근 | `Sprint45E2EIntegrationTest.adminApi_rejectsRegularUser` | Integration | 403 + `FORBIDDEN` |
| TC-T1-051 | RQ-NFR-162 | FN-ADMIN-119-BE | `GET /api/v1/admin/users` | admin 미인증 | `AdminControllerTest.listUsers_withoutAuth_returnsUnauthorized` | Integration | 401 |
| TC-T1-052 | RQ-NFR-162 | FN-ADMIN-116-BE | `GET /api/v1/admin/users` | USER 역할 admin 접근 | `AdminControllerTest.listUsers_withUserRole_returnsForbidden` | Integration | 403 |

## 2. 증거 업로드 · 해시 · 검증

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T1-010 | RQ-REQ-047 | FN-REQ-047-BE | `POST .../upload` | MP4 업로드 성공 | `EvidenceControllerTest.shouldUploadFile` | Integration | 200 + `evidenceId` |
| TC-T1-011 | RQ-REQ-047 | FN-REQ-047-BE | `FileValidationService` | MP4/MOV만 허용 | `FileValidationServiceTest` (MP4/MOV pass) | Unit | 검증 통과 |
| TC-T1-012 | RQ-REQ-047 | FN-REQ-047-BE | `FileValidationService` | 이미지·음성 거부 | `FileValidationServiceTest` (image/audio fail) | Unit | `UnsupportedFileTypeException` |
| TC-T1-013 | RQ-REQ-047 | FN-REQ-047-BE | `POST .../upload` | 미지원 형식 API 응답 | `FileValidationIntegrationTest` (unsupported) | Integration | 400 + `UNSUPPORTED_FILE_TYPE` |
| TC-T1-014 | RQ-PER-154 | FN-PER-154-BE | `POST .../upload` | 2GB 초과 | `FileValidationIntegrationTest` (size exceeded) | Integration | 413 + `FILE_TOO_LARGE` |
| TC-T1-015 | RQ-REQ-047 | FN-REQ-047-BE | `HashService` | 동일 파일 동일 해시 | `HashServiceTest` · `EvidenceControllerTest.upload_sameFileTwice_returnsSameHash` | Unit+Int | SHA-256 64자 hex 동일 |
| TC-T1-016 | RQ-REQ-047 | FN-REQ-047-BE | 업로드 | 수정 파일 다른 해시 | `EvidenceControllerTest.upload_modifiedFile_returnsDifferentHash` | Integration | 해시 상이 |
| TC-T1-017 | RQ-REQ-047 | FN-REQ-047-BE | DB | 해시 DB 저장 | `EvidenceControllerTest.upload_success_persistsEvidenceWithHash` | Integration | `evidences.original_hash` 저장 |

## 3. 비동기 분석

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T1-020 | RQ-REQ-049 | FN-REQ-049-BE | `POST .../analyze` | 분석 요청 생성 | `EvidenceControllerTest.startAnalysis_success_recordsAnalysisRequestedCustodyLog` | Integration | 200 + `AnalysisRequests` QUEUED |
| TC-T1-021 | RQ-REQ-049 | FN-REQ-049-BE | Local worker | 커밋 후 분석 완료 | `LocalAnalysisJobEnqueuerIntegrationTest` | Integration | status → COMPLETED |
| TC-T1-022 | RQ-REQ-049 | FN-REQ-049-BE | 큐 실패 | publish 실패 시 FAILED + CoC | `EvidenceControllerTest.startAnalysis_queuePublishFailure_recordsErrorOccurredCustodyLog` | Integration | FAILED + `ERROR_OCCURRED` |
| TC-T1-023 | RQ-REQ-049 | FN-REQ-049-BE | AI JSON 수신 | COMPLETED 결과 저장 | `AnalysisAiResultIntegrationTest.applyAiResult_persistsCompletedAnalysis` | Contract+Int | `riskLevel`, module ≥5 |
| TC-T1-024 | RQ-REQ-049 | FN-REQ-049-BE | AI JSON | FAILED 처리 | `AnalysisAiResultIntegrationTest.applyAiResult_marksFailedWhenAiReturnsFailed` | Contract+Int | FAILED + `errorCode` |
| TC-T1-025 | RQ-DTL-057 | FN-DTL-057-BE | detail | AI 결과 → riskLevel | `Sprint45E2EIntegrationTest.aiResultToDetail_e2ePipeline` | Integration | `analysisInfo.riskLevel=HIGH` |
| TC-T1-026 | RQ-REQ-049 | FN-REQ-049-BE | `GET .../analysis-status` | 실패 시 errorCode | `EvidenceControllerTest.getAnalysisStatus_whenFailed_returnsErrorDetails` | Integration | `errorCode`·`errorMessage` 존재 |
| TC-T1-027 | RQ-REQ-049 | FN-REQ-049-BE | `DELETE .../analysis` | QUEUED 중단 | `EvidenceControllerTest.cancelAnalysis_whenQueued_succeeds` | Integration | 204 |

## 4. CoC · 무결성

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T1-030 | RQ-REQ-051 | FN-REQ-051-BE | CustodyLogs | 업로드 시 3건 + 체인 | `EvidenceControllerTest.upload_success_recordsCustodyLogs` | Integration | EVIDENCE_UPLOADED 등 3건, 체인 연결 |
| TC-T1-031 | RQ-REQ-051 | FN-REQ-051-BE | `CustodyLogService` | 체인 검증 | `CustodyLogServiceTest.verifyTargetChain_returnsValidForLinkedEvidenceLogs` | Unit | valid=true |
| TC-T1-032 | RQ-HIS-107 | FN-HIS-107-BE | `GET .../coc/verify` | CoC API | `Sprint45E2EIntegrationTest.integrityAndCocVerify_endpoints` | Integration | 200 + `valid` boolean |
| TC-T1-033 | RQ-REQ-050 | FN-REQ-050-BE | Manifest | 분석 copy Manifest 서명 | `EvidenceManifestServiceTest` | Unit | X.509 서명 생성 |
| TC-T1-034 | RQ-DTL-075 | FN-DTL-075-BE | detail | Manifest·서명 필드 | `EvidenceControllerTest.getEvidenceDetail_afterAnalysisStart_includesManifestAndSignature` | Integration | `manifestInfo`·`signatureInfo` |

## 5. 상세 · 대시보드

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T1-040 | RQ-DTL-053 | FN-DTL-053-BE | `GET .../detail` | FE 호환 필드 | `EvidenceControllerTest.getEvidenceDetail_returnsFrontendCompatibleFields` | Integration | `evidenceInfo`·`analysisInfo` |
| TC-T1-041 | RQ-DTL-084 | FN-DTL-084-BE | `GET .../reports/pdf` | PDF + CoC | `Sprint45E2EIntegrationTest.reportPdf_recordsReportCustodyLogs` | Integration | PDF 200 + REPORT_CREATED/DOWNLOADED |
| TC-T1-042 | RQ-DSH-043 | FN-DSH-043-BE | `GET .../stats` | 4카드 통계 | `EvidenceControllerTest.shouldReturnMediaStats` | Integration | 4개 count 필드 |
| TC-T1-043 | RQ-DSH-044 | FN-DSH-044-BE | `GET .../stats/trend` | 7일 추이 | `EvidenceControllerTest.shouldReturnAnalysisTrend` | Integration | `points[]` 7일 |
| TC-T1-044 | RQ-DSH-045 | FN-DSH-045-BE | `GET .../stats/recent` | 최근 분석 | `EvidenceControllerTest.shouldReturnRecentAnalyses` | Integration | 최신 요청 1건/증거 |

## 6. 보안 · 에러 계약

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T1-060 | RQ-SEC-153 | FN-SEC-153-BE | `GET .../integrity/verify` | 정상 증거 | `EvidenceControllerTest.verifyIntegrity_validEvidence_returnsOk` | Integration | 200 + `valid=true` |
| TC-T1-061 | RQ-SEC-153 | FN-SEC-153-BE | `GET .../integrity/verify` | 서명 실패 | `EvidenceControllerTest.verifyIntegrity_invalidSignature_returnsConflict` | Integration | 409 + `errorCode` |
| TC-T1-062 | RQ-SEC-153 | FN-SEC-153-BE | detail | 무결성 실패 알림 | `EvidenceControllerTest.getEvidenceDetail_onIntegrityFailure_createsSecurityAlert` | Integration | SECURITY_ALERT 생성 |
| TC-T1-070 | RQ-COM-* | — | `GlobalExceptionHandler` | 표준 에러 JSON | `GlobalExceptionHandlerTest` | Unit | `success=false` + `errorCode` |

## 7. Tier 1 수동 E2E (스테이징 · SK-469)

| TC-ID | RQ-ID | 대상 | 케이스 요약 | 유형 | Expected | 비고 |
| :--- | :--- | :--- | :--- | :---: | :--- | :--- |
| TC-T1-M01 | RQ-REQ-047~049, DTL-053 | 전체 플로우 | 로그인→업로드→analyze→AI완료→detail | Manual | 한 번에 성공 | `ANALYSIS_WORKER_MODE=ai` |
| TC-T1-M02 | RQ-REQ-048 | S3 | original/ 객체 생성 | Manual | 버킷에 파일 존재 | INF 확인 |
| TC-T1-M03 | RQ-REQ-049 | RabbitMQ | analysis queue 메시지 | Manual | ai-json §2 형식 | AI팀 대조 |
| TC-T1-M04 | SK-1060 | detail | GPU 실결과 → FE 표시 | Manual | moduleResults·frameRisks | FE+BE |

---

# Tier 2 — 여유 시 (Compare · 블록체인 · 부가 API · NFR)

> Tier 1 Pass 후 진행. 발표 시 "확장 검증 완료" 영역.

## 8. Compare 비교 검증

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T2-001 | RQ-CMP-091 | FN-CMP-091-BE | `GET /compare/originals` | 원본 목록 | `FeatureApiControllerTest.compare_listOriginalsAndFileInfo` | Integration | `content[]` |
| TC-T2-002 | RQ-CMP-092~093 | FN-CMP-093-BE | `POST /compare/verify` | 비교 실행 | `FeatureApiControllerTest.compare_verifyAndGetResult` | Integration | `verdict`·`items[]` |
| TC-T2-003 | RQ-CMP-094 | FN-CMP-094-BE | `CompareItemEvaluator` | 해시 비교 판정 | `CompareItemEvaluatorTest.determineVerdict_matchingHashReturnsOriginalMatch` | Unit | MATCH / TAMPERED |
| TC-T2-004 | RQ-CMP-103 | FN-CMP-103-BE | compare | 블록체인 해시 대조 | `FeatureApiControllerTest.compare_verifyUsesAnchoredBlockchainHash` | Integration | BLOCKCHAIN_HASH MATCH |
| TC-T2-005 | RQ-CMP-104 | FN-CMP-104-BE | compare PDF | 비교 PDF | `FeatureApiControllerTest.compare_verifyAndGetResult` (pdf 부분) | Integration | PDF 200 + X-Report-Hash |
| TC-T2-006 | RQ-CMP-* | — | `POST /compare/cancel` | 취소 | `FeatureApiControllerTest.compare_cancel_returnsNoContent` | Integration | 204 |

## 9. 블록체인 · Manifest · Recovery

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T2-010 | RQ-REQ-052 | FN-REQ-052-BE | `BlockchainAnchorService` | 증거 해시 앵커 | `BlockchainAnchorServiceTest` | Unit | ANCHORED + txHash |
| TC-T2-011 | RQ-DTL-078 | FN-DTL-078-BE | `GET .../blockchain` | 앵커 상태 조회 | `FeatureApiControllerTest.evidenceBlockchain_status` | Integration | `evidenceHashAnchor.status` |
| TC-T2-012 | RQ-DTL-079 | FN-DTL-079-BE | detail | hashValid·explorer URL | `FeatureApiControllerTest.evidenceDetail_blockchainIntegrityAndExplorerUrl` | Integration | `hashValid=true` |
| TC-T2-013 | RQ-SEC-151 | FN-SEC-151-BE | Merkle | Merkle 유틸 | `MerkleTreeUtilTest` | Unit | root 일관성 |
| TC-T2-014 | RQ-DTL-071 | FN-DTL-071-BE | Recovery Score | 점수 계산 | `RecoveryScoreServiceTest` | Unit | 0~100 범위 |
| TC-T2-015 | RQ-REQ-050 | FN-REQ-050-BE | PKCS#8 서명 | Manifest 서명·검증 | `Pkcs8ManifestSignatureServiceTest` | Unit | sign→verify OK |
| TC-T2-M01 | RQ-SEC-151~152 | http 앵커 | 블록체인 실연동 | Manual | 실 TX hash | INF URL 필요 |

## 10. 알림 · 설정 · 마이페이지 · v2 사건

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T2-020 | RQ-COM-015 | FN-COM-015-BE | notifications | 목록·읽음 | `FeatureApiControllerTest.notifications_listAndMarkRead` | Integration | `unreadCount` 감소 |
| TC-T2-021 | RQ-COM-009 | FN-COM-009-BE | settings | 조회·수정 | `FeatureApiControllerTest.userSettings_defaultAndUpdate` | Integration | themeMode 반영 |
| TC-T2-022 | RQ-MY-* | FN-MY-*-BE | mypage | 분석 이력 | `MyPageControllerTest` | Integration | `content`·`totalElements` |
| TC-T2-023 | — | — | v2 workflow | exclude/replace/rename | `CaseWorkflowControllerTest` | Integration | lifecycleStatus 변경 |
| TC-T2-024 | — | — | 빈 사건 | `POST /cases` | `EmptyCaseRegistrationControllerTest` | Integration | evidences=[] |
| TC-T2-025 | — | — | 보고서 목록 | `GET /reports` | `ReportControllerTest.shouldListReports` | Integration | `content[]` |
| TC-T2-026 | — | — | RBAC | reviewer 배정 | `CaseRbacPhase2ControllerTest` | Integration | `reviewStatus=REVIEW_ASSIGNED` |
| TC-T2-027 | RQ-REQ-051 | — | access-events | 열람 감사 | `EvidenceAccessAuditControllerTest` | Integration | 204 + CoC |

## 11. 관리자 · 가입

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T2-030 | RQ-ADMIN-127 | FN-ADMIN-127-BE | approve | 가입 승인 | `AdminControllerTest.approvePendingUser_recordsApprovedStatusAndCoC` | Integration | APPROVED |
| TC-T2-031 | RQ-ADMIN-128 | FN-ADMIN-128-BE | reject | 가입 반려 | `AdminControllerTest.rejectPendingUser_recordsRejectedStatus` | Integration | REJECTED |
| TC-T2-032 | RQ-ADMIN-* | — | suspend | 계정 정지 | `AdminControllerTest.suspendApprovedUser_recordsSuspendedStatusAndBlocksLogin` | Integration | SUSPENDED + 로그인 차단 |
| TC-T2-033 | RQ-ADMIN-120 | FN-ADMIN-120-BE | dashboard stats | 관리자 통계 | `AdminControllerTest.getDashboardStats_withAdmin_returnsCounts` | Integration | pendingUsers 등 |
| TC-T2-034 | RQ-ADMIN-150 | FN-ADMIN-150-BE | analysis-stats | 분석 통계 | `AdminControllerTest.getAnalysisStats_withAdmin_returnsAnalysisStats` | Integration | weeklyPoints[7] |
| TC-T2-035 | RQ-SIGNUP-* | FN-SIGNUP-*-BE | signup | 회원가입 | `SignupControllerTest` | Integration | 201 또는 validation |

## 12. AI 컨트랙트 · 시각화 · NFR

| TC-ID | RQ-ID | FN-BE-ID | 대상 | 케이스 요약 | 테스트 (자동) | 유형 | Expected |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| TC-T2-040 | SK-402 | FN-REQ-049-BE | AI JSON | frameRisks 재수신 갱신 | `AnalysisAiResultIntegrationTest.applyAiResult_updatesExistingResultWithFrameRisks` | Contract | timeline module 갱신 |
| TC-T2-041 | RQ-DTL-060~061 | FN-DTL-060-BE | detail | frameRisks·segments | `FeatureApiControllerTest.evidenceDetail_includesVisualizationFields` | Integration | `frameRisks[]`·`suspiciousSegments[]` |
| TC-T2-042 | SK-402 | FN-DTL-082-BE | detail | modelName/Version | `FeatureApiControllerTest.evidenceDetail_includesModuleModelFields` | Integration | moduleResults 메타 |
| TC-T2-043 | RQ-DTL-084 | FN-DTL-084-BE | report verify | PDF 해시 검증 | `FeatureApiControllerTest.evidenceAnalysisReport_pdf` | Integration | `reports/verify` valid=true |
| TC-T2-044 | — | `AnalysisJobMessageFactory` | MQ payload | ai-json §2 필드 | `AnalysisJobMessageFactoryTest` | Contract | fileType=video |
| TC-T2-M10 | RQ-PER-155 | FN-PER-155-BE | stats API | 응답 시간 | Manual (k6/Postman) | NFR | < 목표 ms (팀 합의) |
| TC-T2-M11 | RQ-PER-154 | FN-PER-154-BE | analyze | 비동기 응답성 | Manual | NFR | 즉시 200, 백그라운드 완료 |
| TC-T2-M12 | SK-838 | — | FE-BE | JSON 필드 통일 | Manual | Contract | FE 타입과 일치 |

---

## 실행 기록 템플릿 (발표용 1장)

| 항목 | Tier 1 | Tier 2 |
| :--- | :---: | :---: |
| **자동 TC 수** | 42 + 수동 4 | 35 + 수동 4 |
| **실행일** | *(기록)* | *(기록)* |
| **Pass** | | |
| **Fail** | | |
| **Skip** | | |
| **실행 명령** | `.\gradlew test` | 동일 (+ 수동/k6) |
| **보증 범위** | 핵심 수사 여정 | Compare·블록체인·부가 API·NFR |

---

## Fail 우선순위 (멘토 §3.6)

| 등급 | Tier 1 예시 | 대응 |
| :--- | :--- | :--- |
| **Critical** | TC-T1-010 업로드 실패, TC-T1-025 detail 비어 있음 | 즉시 수정 · E2E 중단 |
| **Medium** | TC-T1-M03 RabbitMQ 미연동 | 스테이징 설정 · simulated 폴백 문서화 |
| **Minor** | TC-T2-M10 NFR 미달 | 캐시·쿼리 튜닝 또는 목표치 조정 합의 |

---

## 관련 문서

- [../requirements/traceability.md](../requirements/traceability.md) — RQ↔FN
- [../api/specification.md](../api/specification.md) — API 정본
- [../integrations/ai-json.md](../integrations/ai-json.md) — AI 컨트랙트
- [../PROJECT_STATUS.md](../PROJECT_STATUS.md) — 구현 현황
