# 백엔드 소스 파일 역할 맵

> **목적:** `src/main/java/com/example/demo` 이하 주요 파일이 **무슨 역할**을 하는지 빠르게 찾기  
> **기준 브랜치:** `develop` (2026-07-07)  
> **관련:** [teams/backend.md](../teams/backend.md) · [api/specification.md](../api/specification.md)

---

## 읽는 법

| 레이어 | 역할 한 줄 |
|--------|------------|
| `controller` | HTTP 입출력만. 비즈니스 로직·try-catch 금지 |
| `service` | 트랜잭션·도메인 규칙. `BusinessException` throw |
| `repository` | JPA DB 접근 |
| `domain` | 엔티티·Enum (DB 테이블과 1:1) |
| `dto` | API 요청/응답 JSON 형태 |
| `config` | Bean·보안·외부 연동 설정 |
| `messaging` | RabbitMQ 발행/구독 |
| `security` | JWT·인증 필터 |
| `exception` | 공통 예외·`@RestControllerAdvice` |
| `util` | 무상태 헬퍼 |
| `scheduler` | `@Scheduled` 배치 |

```text
Client → controller → service → repository → DB
                    ↘ messaging → RabbitMQ → AI
                    ↘ S3 / Redis / Fabric HTTP
```

---

## 루트

| 파일 | 역할 |
|------|------|
| `DemoApplication.java` | Spring Boot 진입점 (`main`) |
| `build.gradle` | 의존성·Java 17·테스트·JaCoCo 등 빌드 설정 |
| `Dockerfile` | EKS 배포용 컨테이너 이미지 빌드 |
| `.github/workflows/deploy.yml` | `develop` push 시 ECR 빌드 + infra-forensic 이미지 태그 갱신 |
| `.github/workflows/secret-scan.yml` | PR 시 `.env`/PEM/Private Key 유출 검사 |

---

## `config/` — 설정·Bean

| 파일 | 역할 |
|------|------|
| `SecurityConfig.java` | Spring Security 필터 체인, `/api/v1/**` 인증 규칙 |
| `CorsConfig.java` | FE 도메인 CORS 허용 |
| `WebMvcConfig.java` | MVC 인터셉터·리소스 핸들러 |
| `JwtProperties.java` | JWT 만료·시크릿 키 프로퍼티 |
| `JwtSecretValidator.java` | 기동 시 JWT 시크릿 길이/존재 검증 |
| `PasswordEncoderConfig.java` | BCrypt `PasswordEncoder` Bean |
| `S3Config.java` | AWS S3 클라이언트 (IRSA/로컬 프로필) |
| `RedisConfig.java` | Redis 연결 (리프레시 토큰 저장) |
| `RabbitMqConfig.java` | RabbitMQ Exchange·Queue·Binding |
| `AnalysisWorkerProperties.java` | `analysis.worker.mode` — `local` / `simulated` / `ai` |
| `AnalysisMessagingProperties.java` | 분석 job/result 큐 이름·라우팅 키 |
| `BlockchainAnchorProperties.java` | Fabric Gateway URL·모드·타임아웃 |
| `EvidenceManifestProperties.java` | 증거 manifest 저장·서명 관련 경로 |
| `EvidenceManifestSigningProperties.java` | manifest 서명 키 경로/PEM |
| `ManifestSigningConfig.java` | manifest 서명 서비스 Bean 조립 |
| `VideoFrameAnalysisProperties.java` | 영상 프레임 추출·분석 표시 설정 |
| `ReadinessProperties.java` | 분석 전 화질 검사 — Python 스크립트 경로·샘플링 |
| `OpenApiConfig.java` | Swagger UI·Bearer JWT 연동 |
| `LocalDevUserInitializer.java` | `local` 프로필 테스트 계정 시드 |
| `H2ErdSchemaInitializer.java` | H2 테스트 DB 스키마 보정 |

---

## `controller/` — REST API

공통 prefix는 `EvidenceApiPaths.BASE` = `/api/v1/evidences` 등. 상세 계약은 [api/specification.md](../api/specification.md).

| 파일 | 주요 API·역할 |
|------|----------------|
| `EvidenceApiPaths.java` | 증거 API path 상수 |
| `AuthController.java` | `POST /api/auth/login`, refresh, logout, signup, loginId 중복 확인 |
| `InviteCodeController.java` | 가입 초대코드 검증 |
| `UserController.java` | 프로필·설정 조회/수정 |
| `OrganizationController.java` | 부서 목록 |
| `EvidenceController.java` | 업로드, 분석 시작, 상세, readiness, 분석 상태/취소 |
| `EvidenceDashboardController.java` | 대시보드 통계·최근 분석·트렌드 |
| `EvidenceWorkflowController.java` | v2 사건·증거 워크플로 (제외/대체/역할) |
| `EvidenceAccessAuditController.java` | 증거 조회·캡처 시도 감사 로그 |
| `EvidenceVerificationController.java` | 무결성·CoC·블록체인 검증 API |
| `EvidenceReportController.java` | 증거별 PDF 리포트 다운로드 |
| `CaseController.java` | 사건 생성·목록·상세·리뷰어 지정 |
| `CompareController.java` | 비교검증 업로드·결과 |
| `MyPageController.java` | 분석 이력·사건 요약 |
| `NotificationController.java` | 알림 목록·읽음 처리 |
| `ReportController.java` | 리포트 목록 (사용자) |
| `AdminUserController.java` | 관리자 사용자 CRUD·승인 |
| `AdminEvidenceController.java` | 관리자 증거 목록·상세·삭제 |
| `AdminDashboardController.java` | 관리자 대시보드·분석 통계 |
| `AdminLogController.java` | 관리자 감사 로그 |
| `AdminProfileController.java` | 관리자 본인 프로필 |
| `AdminInviteCodeController.java` | 초대코드 발급 |
| `AdminBlockchainController.java` | 블록체인 앵커 조회 (관리) |

---

## `domain/` — 엔티티

| 파일 | DB 테이블·역할 |
|------|----------------|
| `User.java` | 사용자·역할·조직·승인 상태 |
| `UserSetting.java` | 테마·알림·표시 설정 |
| `InviteCode.java` | 가입 초대코드 |
| `CaseProfile.java` | 사건 프로필·대표 증거·리뷰 상태 |
| `Evidence.java` | 증거 원본·사본 S3 경로·해시·사건명 |
| `EvidenceMetadata.java` | ffprobe 메타·readiness JSON |
| `EvidenceManifest.java` | 분석용 manifest·서명 |
| `AnalysisRequest.java` | 분석 요청·큐 상태·진행률 |
| `AnalysisResult.java` | AI 분석 결과 요약·위험도 |
| `AnalysisModuleResult.java` | 모듈별 점수·타임라인 JSON |
| `CompareVerification.java` | 비교검증 기록 |
| `CustodyLog.java` | Chain of Custody 해시 체인 로그 |
| `BlockchainAnchor.java` | Fabric 앵커 기록 |
| `Notification.java` | 사용자 알림 |
| `Report.java` | PDF 리포트 메타·S3 경로 |

### `domain/enums/`

| 파일 | 용도 |
|------|------|
| `AnalysisStatus.java` | `QUEUED` · `ANALYZING` · `COMPLETED` · `FAILED` |
| `EvidenceStatus.java` | 증거 분석 UI 상태 매핑용 |
| `EvidenceLifecycleStatus.java` | `ACTIVE` · `EXCLUDED` 등 v2 생명주기 |
| `EvidenceRole.java` | `PRIMARY` · `SUPPORTING` · `COMPARE_BASE` 등 |
| `FileType.java` | `VIDEO` · `AUDIO` · `IMAGE` |
| `CopyStatus.java` | 분석용 S3 사본 상태 |
| `ExtractionStatus.java` | 메타데이터 추출 성공/부분/실패 |
| `RiskLevel.java` | `LOW` · `MEDIUM` · `HIGH` |
| `UserRole.java` | `USER` · `ADMIN` · `REVIEWER` · `ORG_ADMIN` |
| `UserStatus.java` | `PENDING` · `APPROVED` · `REJECTED` · `SUSPENDED` |
| `ReadinessTier.java` | `GOOD` · `CAUTION` · `POOR` · `BLOCK` |
| `ReadinessSource.java` | `FFPROBE` · `FRAME_SAMPLE` |
| `NotificationType.java` | 알림 종류 |
| `SecurityAlertCode.java` | 무결성·블록체인 보안 알림 코드 |
| `BlockchainAnchorStatus.java` | 앵커 성공/실패 |
| `BlockchainAnchorType.java` | 앵커 유형 |
| `CompareVerdict.java` | 비교검증 판정 |
| `CompareItemResult.java` | 항목별 MATCH/MISMATCH |
| `CompareSignatureStatus.java` | 비교 서명 상태 |
| `CompareBlockchainStatus.java` | 비교 블록체인 상태 |
| `SignatureStatus.java` | manifest 서명 상태 |
| `CustodyTargetType.java` | CoC 대상 유형 |
| `EvidenceAccessEventType.java` | `VIEW` · `CAPTURE_ATTEMPT` 등 |
| `InviteStatus.java` | 초대코드 사용 상태 |
| `OrgType.java` | 조직 유형 |
| `ThemeMode.java` | `LIGHT` · `DARK` |
| `ListViewMode.java` | UI 목록 표시 모드 |
| `DateDisplayFormat.java` | 날짜 표시 형식 |

---

## `repository/` — JPA

| 파일 | 대상 엔티티 |
|------|-------------|
| `UserRepository.java` | `User` |
| `UserSettingRepository.java` | `UserSetting` |
| `InviteCodeRepository.java` | `InviteCode` |
| `CaseProfileRepository.java` | `CaseProfile` |
| `EvidenceRepository.java` | `Evidence` |
| `EvidenceMetadataRepository.java` | `EvidenceMetadata` |
| `EvidenceManifestRepository.java` | `EvidenceManifest` |
| `AnalysisRequestRepository.java` | `AnalysisRequest` |
| `AnalysisResultRepository.java` | `AnalysisResult` |
| `AnalysisModuleResultRepository.java` | `AnalysisModuleResult` |
| `CompareVerificationRepository.java` | `CompareVerification` |
| `CustodyLogRepository.java` | `CustodyLog` |
| `BlockchainAnchorRepository.java` | `BlockchainAnchor` |
| `NotificationRepository.java` | `Notification` |
| `ReportRepository.java` | `Report` |

---

## `service/` — 비즈니스 로직

### `service/auth/`

| 파일 | 역할 |
|------|------|
| `AuthService.java` | 로그인·토큰 발급·갱신·로그아웃 |
| `SignupService.java` | 회원가입·중복 검사 |
| `InviteCodeService.java` | 초대코드 검증·소비 |
| `RefreshTokenRedisService.java` | 리프레시 토큰 Redis 저장/폐기 |

### `service/user/`

| 파일 | 역할 |
|------|------|
| `UserService.java` | 프로필 조회·수정 |
| `UserSettingsService.java` | 사용자 설정 |
| `OrganizationService.java` | 부서 목록 |
| `MyPageService.java` | 분석 이력·사건 목록 (RBAC 필터 포함) |

### `service/evidence/`

| 파일 | 역할 |
|------|------|
| `FileService.java` | 멀티파트 업로드·해시·S3·메타 추출 |
| `FileValidationService.java` | 확장자·MIME·크기 검증 |
| `HashService.java` | SHA-256 계산 |
| `MediaService.java` | ffprobe 메타데이터 추출 |
| `EvidenceMetadataService.java` | 메타 DB 저장 |
| `EvidenceCopyService.java` | 분석용 S3 사본 생성·검증 |
| `EvidenceStoragePaths.java` | S3 키 경로 규칙 |
| `EvidenceCancelService.java` | 업로드 취소·증거 reset |
| `EvidenceDetailService.java` | 증거 상세 조회 오케스트레이션 |
| `EvidenceDetailAssembler.java` | 상세 응답 DTO 조립 |
| `CaseDetailAssembler.java` | 사건 상세·증거 목록 DTO |
| `CaseEvidencePresentationService.java` | 사건 내 증거 표시 라벨·진행률 |
| `CaseWorkflowService.java` | v2 제외/대체/대표증거/역할 변경 |
| `EvidenceAccessService.java` | 증거 접근 권한 검사 |
| `EvidenceAccessAuditService.java` | 조회·캡처 감사 이벤트 기록 |
| `EvidenceMediaUrlService.java` | 스트리밍용 presigned URL |

### `service/readiness/` — 분석 전 화질 적합성

| 파일 | 역할 |
|------|------|
| `EvidenceReadinessService.java` | readiness 조회·프레임 검사·analyze 전 품질 확인 |
| `ReadinessEvaluator.java` | ffprobe 메타 기반 tier·confidenceCap 산출 |
| `VideoReadinessRunner.java` | `video_readiness.py` 실행·결과 파싱 |
| `EvidenceReadinessFileService.java` | S3에서 영상 임시 다운로드 |

### `service/analysis/`

| 파일 | 역할 |
|------|------|
| `AnalysisService.java` | 분석 시작·큐 등록·품질 ack 검증 |
| `AnalysisCancelService.java` | 분석 중단 |
| `AnalysisStatusService.java` | 분석 상태·진행률 API |
| `AnalysisWorkerService.java` | local/simulated/ai 모드별 결과 처리 |
| `AnalysisJobEnqueuer.java` | 큐 발행 인터페이스 |
| `AnalysisJobMessageFactory.java` | RabbitMQ job 메시지 생성 |
| `AnalysisResultPersistenceService.java` | AI 결과 DB 저장 |
| `AnalysisResponseResolver.java` | AI JSON → 도메인 매핑 |
| `AnalysisInfoAssembler.java` | 상세 화면 analysisInfo 블록 |
| `AnalysisDetailFormatters.java` | 점수·시간 포맷 |
| `VideoAnalysisDetailsBuilder.java` | 영상 분석 상세 블록 |
| `VideoAnalysisModuleWriter.java` | 모듈 결과·타임라인 저장 |
| `VideoModuleDetailsReader.java` | 저장된 모듈 JSON 읽기 |
| `VideoFrameExtractionService.java` | 의심 프레임 이미지 추출 |
| `SuspiciousSegmentCalculator.java` | 의심 구간 계산 |
| `S3AnalysisAccessService.java` | AI용 presigned URL·버킷 정보 |
| `AnalysisQueueMetricsResolver.java` | 큐 대기 지표 (표시용) |

### `service/custody/`

| 파일 | 역할 |
|------|------|
| `CustodyLogService.java` | 업로드·로그인 등 CoC 이벤트 기록 |
| `AnalysisCustodyLogService.java` | 분석 요청·실패·큐 오류 CoC |
| `ReportCustodyLogService.java` | 리포트 생성 CoC |
| `CustodyChainVerifier.java` | CoC 해시 체인 검증 |
| `CocChainVerificationService.java` | CoC 검증 API |
| `RecoveryScoreService.java` | 메타데이터 복구 점수 (상세 화면) |

### `service/integrity/`

| 파일 | 역할 |
|------|------|
| `IntegrityVerificationService.java` | 원본 해시·manifest·블록체인 교차 검증 |
| `EvidenceIntegrityResult.java` | 검증 결과 묶음 (내부 타입) |

### `service/manifest/`

| 파일 | 역할 |
|------|------|
| `EvidenceManifestService.java` | manifest 생성·서명·조회 |
| `ManifestSignatureService.java` | 서명 인터페이스 |
| `Pkcs8ManifestSignatureService.java` | PKCS8 키로 manifest 서명 |
| `ManifestSigningKeyLoader.java` | PEM/Secret에서 키 로드 |
| `ManifestSigningKeyMaterial.java` | 키·인증서 묶음 |

### `service/blockchain/`

| 파일 | 역할 |
|------|------|
| `BlockchainAnchorService.java` | 해시 Fabric 앵커 요청·스케줄 |
| `BlockchainHashIntegrityEvaluator.java` | 앵커 vs 로컬 해시 비교 |
| `OffchainLogHashService.java` | 오프체인 로그 Merkle 해시 |
| `SignerCertificateHashCalculator.java` | 서명 인증서 해시 |

#### `service/blockchain/client/`

| 파일 | 역할 |
|------|------|
| `BlockchainAnchorClient.java` | 앵커 클라이언트 인터페이스 |
| `HttpBlockchainAnchorClient.java` | EC2 Fabric Gateway HTTP 호출 |
| `SimulatedBlockchainAnchorClient.java` | 로컬 simulated 앵커 |
| `BlockchainAnchorRequest.java` | 앵커 요청 DTO |
| `BlockchainAnchorResult.java` | 앵커 응답 DTO |
| `OffchainRef.java` | 오프체인 참조 필드 |

### `service/compare/`

| 파일 | 역할 |
|------|------|
| `CompareVerificationService.java` | 비교검증 오케스트레이션 |
| `CompareVerificationAssembler.java` | 비교 결과 DTO 조립 |
| `CompareItemEvaluator.java` | 해시·메타·블록체인 항목별 비교 |
| `CompareCandidateFileHandler.java` | 비교 후보 파일 임시 저장 |
| `CompareTrustMetadataAssembler.java` | 비교 화면 신뢰 메타데이터 |

### `service/dashboard/`

| 파일 | 역할 |
|------|------|
| `EvidenceStatsService.java` | 통계·트렌드·최근 분석 |
| `DashboardIntroService.java` | 대시 intro 카드 데이터 |
| `DashboardStatsCache.java` | 통계 캐시 무효화 |

### `service/report/`

| 파일 | 역할 |
|------|------|
| `ReportListService.java` | 리포트 목록 페이징 |
| `ReportPdfService.java` | PDF 생성 (분석·비교) |
| `ReportPdfStorageService.java` | PDF S3 업로드 |
| `ReportContentBuilder.java` | PDF 본문 데이터 조립 |

### `service/notification/`

| 파일 | 역할 |
|------|------|
| `NotificationService.java` | 알림 생성·조회·읽음·보안 알림 |

### `service/admin/`

| 파일 | 역할 |
|------|------|
| `AdminUserService.java` | 사용자 관리·승인·비밀번호 리셋 |
| `AdminEvidenceService.java` | 전체 증거 조회·삭제 |
| `AdminDashboardService.java` | 관리자 KPI |
| `AdminAnalysisStatsService.java` | 주간 분석 통계 |
| `AdminLogService.java` | 감사 로그 페이징 |
| `AdminProfileService.java` | 관리자 프로필 |
| `AdminInviteCodeService.java` | 초대코드 발급 |
| `LogCategoryMapper.java` | CoC 이벤트 → 한글 라벨 |

### `service/package-info.java`

레이어 패키지 설명 (Javadoc).

---

## `dto/` — API 계약 객체

루트 `dto/` — 여러 도메인 공통.

| 파일 | 역할 |
|------|------|
| `StandardErrorResponse.java` | 에러 JSON 표준 (`errorCode`, `message`, `details`) |
| `FileUploadResponse.java` | 업로드 성공 응답 |
| `StartAnalysisRequest.java` | 분석 시작 요청 (`evidenceIds`, `acknowledgeQualityWarning`) |
| `StartAnalysisResponse.java` | 분석 시작 응답·항목별 `queueRegistered` |
| `AnalysisStartResultItem.java` | 증거별 분석 시작 결과 |
| `AnalysisStatusResponse.java` | 분석 상태·진행률 |
| `AnalysisJobMessage.java` | RabbitMQ → AI job 페이로드 |
| `AnalysisResponseMessage.java` | AI → BE 결과 페이로드 |
| `MediaMetadata.java` | ffprobe 추출 메타 |
| `ValidatedFile.java` | 검증된 업로드 파일 메타 |
| `EvidenceStatsResponse.java` | 대시보드 통계 |
| `RecentAnalysisItem.java` | 최근 분석 한 건 |
| `RecentAnalysisResponse.java` | 최근 분석 목록 |
| `AnalysisTrendPoint.java` | 일별 완료 건수 |
| `AnalysisTrendResponse.java` | 트렌드 차트 |
| `LoginRequest.java` / `LoginResponse.java` | 로그인 |
| `TokenResponse.java` | 토큰 재발급 |
| `AuthenticatedTokens.java` | access+refresh 내부 묶음 |
| `DashboardIntroResponse.java` | 대시 intro |
| `DashboardShortcutDto.java` | 바로가기 카드 |
| `DashboardTrustHighlightDto.java` | 신뢰 하이라이트 |
| `IntegrityVerifyResponse.java` | 무결성 검증 결과 |
| `IntegrityCheckItem.java` | 검증 항목 한 줄 |
| `CocChainVerifyResponse.java` | CoC 체인 검증 |
| `ReportVerifyResponse.java` | 리포트 검증 |
| `FrameRiskDto.java` | 프레임별 위험 점수 |
| `FrameAnalysisSpecDto.java` | 프레임 분석 스펙 |
| `SuspiciousSegmentDto.java` | 의심 구간 |
| `ClipRiskDto.java` | 클립 위험도 |
| `PairRiskDto.java` | 프레임 쌍 위험도 |
| `VideoDeepfakeTimelineDto.java` | 딥페이크 타임라인 |
| `BlockchainAnchorRecordDto.java` | 앵커 기록 |
| `BlockchainAnchorStatusResponse.java` | 앵커 상태 API |

### `dto/detail/` — 증거·사건 상세

| 파일 | 역할 |
|------|------|
| `EvidenceDetailResponse.java` | 증거 상세 루트 |
| `EvidenceInfoDto.java` | 파일·해시·업로드 정보 |
| `AnalysisInfoDto.java` | 분석 상태·점수·완료 시각 |
| `IntegrityInfoDto.java` | 무결성 요약 |
| `ManifestInfoDto.java` | manifest·서명 |
| `SignatureInfoDto.java` | 플랫폼 서명 정보 |
| `BlockchainInfoDto.java` | 앵커 TX |
| `ModelScoreDto.java` | 모델별 점수 |
| `ModuleResultDto.java` | 모듈 결과 한 건 |
| `ModuleTimelineDto.java` | TimeSformer/GMFlow 타임라인 |
| `RecoveryScoreDto.java` | 메타 복구 점수 |
| `VideoMetadataDto.java` | 영상 메타 표시 |
| `CocLogDto.java` | CoC 이벤트 한 줄 |
| `CaseDetailResponse.java` | 사건 상세 |
| `CaseEvidenceSummaryDto.java` | 사건 내 증거 요약 |

### `dto/readiness/`

| 파일 | 역할 |
|------|------|
| `EvidenceReadinessResponse.java` | FE readiness API 응답 |
| `ReadinessSnapshot.java` | DB `readiness_json` 내부 스냅샷 |
| `ReadinessVideoMetadataDto.java` | 영상 해상도·fps·길이 |
| `ReadinessFrameMetricsDto.java` | blur/blockiness/FFT 지표 |
| `ReadinessSpatialDto.java` | 공간 영역 메트릭 |
| `ReadinessMetricAggregateDto.java` | 집계 메트릭 |

### `dto/caseworkflow/`

| 파일 | 역할 |
|------|------|
| `CreateCaseRequest.java` | 빈 사건 생성 |
| `UpdateCaseNameRequest.java` | 사건명 변경 |
| `AssignCaseReviewerRequest.java` | 리뷰어 지정 |
| `ExcludeEvidenceRequest.java` | 증거 사용 제외 |
| `SetEvidenceRoleRequest.java` | 증거 역할 변경 |
| `SetRepresentativeEvidenceRequest.java` | 대표 증거 지정 |

### `dto/compare/`

| 파일 | 역할 |
|------|------|
| `CompareVerifyResponse.java` | 비교검증 실행 결과 |
| `CompareResultResponse.java` | 비교 결과 상세 |
| `CompareSummaryDto.java` | 요약 판정 |
| `CompareItemDto.java` | 항목별 비교 |
| `CompareFileInfoDto.java` | 파일 정보 |
| `CompareSignatureInfoDto.java` | 서명 비교 |
| `CompareBlockchainInfoDto.java` | 블록체인 비교 |
| `CompareOriginalPageResponse.java` | 원본 사건 선택용 |

### `dto/mypage/`

| 파일 | 역할 |
|------|------|
| `AnalysisHistoryItemResponse.java` | 분석 이력 한 건 |
| `AnalysisHistoryPageResponse.java` | 분석 이력 페이지 |
| `CaseSummaryResponse.java` | 사건 요약 카드 |

### `dto/admin/`

관리자 화면용 페이지·상세·요청 DTO (`AdminUser*`, `AdminEvidence*`, `AdminLog*`, `CreateInviteCodeRequest` 등).

### `dto/user/` · `dto/signup/` · `dto/notification/` · `dto/report/` · `dto/evidence/`

| 패키지 | 역할 |
|--------|------|
| `user/` | 프로필·설정 요청/응답 |
| `signup/` | 회원가입·초대코드·부서 목록 |
| `notification/` | 알림 목록·일괄 읽음 |
| `report/` | 리포트 목록·요약 |
| `evidence/` | `EvidenceAccessEventRequest` — 감사 이벤트 body |

---

## `messaging/` — RabbitMQ

| 파일 | 역할 |
|------|------|
| `AnalysisJobEnqueuer.java` | 큐 발행 인터페이스 (service와 동명) |
| `RabbitMqAnalysisJobEnqueuer.java` | AI 모드 job publish |
| `LocalAnalysisJobEnqueuer.java` | local 모드 — 트랜잭션 후 시뮬 결과 |
| `RabbitMqAnalysisResultConsumer.java` | AI 결과 큐 consume → DB 반영 |
| `RabbitMqAnalysisQueueConsumer.java` | (레거시/보조) 큐 consume |

---

## `security/`

| 파일 | 역할 |
|------|------|
| `JwtTokenProvider.java` | JWT 생성·파싱·검증 |
| `JwtAuthenticationFilter.java` | Bearer 토큰 필터 |
| `AuthUserResolver.java` | `@Controller`에서 현재 `User` 주입 |
| `AuthCookieSupport.java` | HttpOnly refresh 쿠키 |
| `SecurityErrorResponses.java` | 401/403 JSON 표준 |
| `RestAuthenticationEntryPoint.java` | 미인증 401 |
| `RestAccessDeniedHandler.java` | 권한 없음 403 |
| `JsonErrorResponseWriter.java` | JSON 에러 출력 유틸 |
| `SignupRateLimitService.java` | 가입 요청 rate limit |
| `SignupRateLimitInterceptor.java` | rate limit MVC 인터셉터 |
| `RateLimitDecision.java` | limit 판정 결과 |

---

## `exception/`

| 파일 | 역할 |
|------|------|
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` — 전역 4xx/5xx |
| `BusinessException.java` | 비즈니스 규칙 위반 (의도적 4xx) |
| `AuthException.java` | 인증·가입 오류 |
| `AdminException.java` | 관리자 API 오류 |
| `AnalysisCopyException.java` | S3 사본 생성/검증 실패 |
| `AnalysisDispatchException.java` | RabbitMQ 발행 실패 |
| `HashGenerationException.java` | 해시 계산 실패 |
| `FileSizeExceededException.java` | 용량 초과 |
| `InvalidMediaFileException.java` | 미디어 파싱 실패 |
| `UnsupportedFileTypeException.java` | 허용되지 않은 확장자 |
| `InvalidInviteCodeException.java` | 초대코드 무효 |
| `DuplicateSignupFieldException.java` | 가입 중복 필드 |

---

## `util/`

| 파일 | 역할 |
|------|------|
| `AnalysisStatusMapper.java` | DB status ↔ API status |
| `AiResultMapper.java` | AI JSON 필드 매핑 |
| `CaseKeyNormalizer.java` | 사건 URL 키 정규화 |
| `EvidenceCaseIdResolver.java` | evidence → caseId |
| `OrganizationIdResolver.java` | RBAC 조직 스코프 |
| `UserRoleSupport.java` | 역할·권한 헬퍼 |
| `ClientIpResolver.java` | 프록시 뒤 클라이언트 IP |
| `JsonPayloadWriter.java` | CoC payload JSON 직렬화 |
| `ApiDateTimeFormatter.java` | API 날짜 ISO 포맷 |
| `MerkleTreeUtil.java` | Merkle 루트 계산 |
| `FfprobeCompareHelper.java` | ffprobe JSON 비교 |
| `PdfDocumentWriter.java` | PDF 저수준 작성 |
| `QrCodeImageWriter.java` | 리포트 QR 코드 |

---

## `scheduler/`

| 파일 | 역할 |
|------|------|
| `AnalysisStaleJobReaper.java` | 오래 `ANALYZING`인 job FAILED 처리 |
| `BlockchainAnchorScheduler.java` | 일일 배치 앵커 (설정 시) |

---

## `src/main/resources/`

### 설정 YAML

| 파일 | 역할 |
|------|------|
| `application.yaml` | 공통 설정 (JWT, RabbitMQ, readiness, blockchain) |
| `application-local.yaml` | 로컬 H2·Rabbit 제외·테스트 유저 |
| `application-prod.yaml` | EKS prod 오버라이드 |

### `db/schema/` — 마이그레이션 SQL

| 파일 | 역할 |
|------|------|
| `001_forenshield_erd_v3.*.sql` | 초기 ERD v3 (H2/PostgreSQL) |
| `002_blockchain_anchors_and_reports.*.sql` | 앵커·리포트 테이블 |
| `002_seed_admin.postgresql.sql` | 관리자 시드 |
| `003_seed_login_users.postgresql.sql` | 로그인 테스트 유저 |
| `003_evidence_manifests.postgresql.sql` | manifest 테이블 |
| `003_blockchain_anchor_ledger_fields.postgresql.sql` | 앵커 ledger 컬럼 |
| `004_v2_case_evidence_workflow.postgresql.sql` | v2 사건·증거 워크플로 컬럼 |
| `004_case_profiles_review_status.postgresql.sql` | 리뷰 상태 컬럼 |
| `005_case_review_rbac.postgresql.sql` | RBAC 리뷰어 컬럼 |
| `005_custody_logs_payload_text.postgresql.sql` | CoC payload 타입 |
| `005_evidence_metadata_readiness.*.sql` | `readiness_json` 컬럼 |
| `evidence.sql` | 증거 테이블 참고 스니펫 |
| `forenshield_erd_v3.sql` | ERD 통합 참고 |

### `crypto/`

manifest 서명용 PEM 배치 디렉터리 (실키는 Git 제외, `.gitkeep`만 추적).

---

## `src/test/java/` — 테스트 (요약)

| 패키지 | 대표 파일 | 검증 대상 |
|--------|-----------|-----------|
| `controller/` | `EvidenceControllerTest`, `FeatureApiControllerTest`, `Sprint45E2EIntegrationTest` | REST API·E2E |
| `service/analysis/` | `AnalysisAiResultIntegrationTest`, `AnalysisWorkerServiceTest` | AI 결과 반영 |
| `service/readiness/` | `ReadinessEvaluatorTest`, `EvidenceReadinessAcknowledgementTest` | 화질 tier·ack |
| `service/blockchain/` | `BlockchainAnchorServiceTest` | 앵커 |
| `service/custody/` | `CustodyLogServiceTest`, `RecoveryScoreServiceTest` | CoC |
| `support/` | `AbstractEvidenceIntegrationTest`, `EvidenceApiTestSupport` | 통합 테스트 공통 |

---

## 자주 찾는 흐름 → 파일

| 사용자 행동 | 따라가기 |
|-------------|----------|
| 로그인 | `AuthController` → `AuthService` → `JwtTokenProvider` |
| 영상 업로드 | `EvidenceController.upload` → `FileService` → `MediaService` |
| 분석하기 | `EvidenceController` → `EvidenceReadinessService` → `AnalysisService` → `RabbitMqAnalysisJobEnqueuer` |
| AI 결과 수신 | `RabbitMqAnalysisResultConsumer` → `AnalysisWorkerService` → `AnalysisResultPersistenceService` |
| 상세 화면 | `EvidenceController.detail` → `IntegrityVerificationService` → `EvidenceDetailService` |
| PDF 리포트 | `EvidenceReportController` → `ReportPdfService` |
| 비교검증 | `CompareController` → `CompareVerificationService` |

---

*마지막 업데이트: 2026-07-07*
