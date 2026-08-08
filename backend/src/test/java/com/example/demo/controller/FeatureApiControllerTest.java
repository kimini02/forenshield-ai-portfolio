package com.example.demo.controller;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.ListViewMode;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.ReportPublicationStatus;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.CompareVerificationRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.ReportPublicationSnapshotRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserSettingRepository;
import com.example.demo.service.evidence.HashService;
import com.example.demo.service.notification.NotificationService;
import com.example.demo.service.report.ReportPublicationSnapshotService;
import com.example.demo.security.SignupRateLimitService;
import com.example.demo.support.JwtTestSupport;
import com.example.demo.support.StepUpTestSupport;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FeatureApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private AnalysisModuleResultRepository analysisModuleResultRepository;

    @Autowired
    private CompareVerificationRepository compareVerificationRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportPublicationSnapshotRepository reportPublicationSnapshotRepository;

    @Autowired
    private CaseProfileRepository caseProfileRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private BlockchainAnchorRepository blockchainAnchorRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserSettingRepository userSettingRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private HashService hashService;

    @Autowired
    private SignupRateLimitService signupRateLimitService;

    @Autowired
    private ReportPublicationSnapshotService reportPublicationSnapshotService;

    private String accessToken;
    private String stepUpToken;
    private User testUser;
    private Evidence evidence;

    @BeforeEach
    void setUp() throws Exception {
        signupRateLimitService.reset();
        compareVerificationRepository.deleteAll();
        custodyLogRepository.deleteAll();
        blockchainAnchorRepository.deleteAll();
        reportPublicationSnapshotRepository.deleteAll();
        reportRepository.deleteAll();
        caseProfileRepository.deleteAll();
        notificationRepository.deleteAll();
        userSettingRepository.deleteAll();
        analysisModuleResultRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .loginId("1111")
                .email("1111@test.local")
                .password(passwordEncoder.encode("2222"))
                .name("테스트 사용자")
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(testUser.getUserId())
                .caseName("비교 테스트")
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc123def4567890abc123def4567890abc123def4567890abc123def4567890")
                .originalStoragePath("s3://bucket/sample.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        accessToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");
        stepUpToken = StepUpTestSupport.issueStepUpToken(mockMvc, accessToken, "2222");
    }

    @AfterEach
    void tearDown() {
        compareVerificationRepository.deleteAll();
        custodyLogRepository.deleteAll();
        blockchainAnchorRepository.deleteAll();
        reportPublicationSnapshotRepository.deleteAll();
        reportRepository.deleteAll();
        caseProfileRepository.deleteAll();
        notificationRepository.deleteAll();
        userSettingRepository.deleteAll();
        analysisModuleResultRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void userSettings_defaultAndUpdate() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateDisplayFormat").value("ISO"))
                .andExpect(jsonPath("$.analysisCompleteNotificationEnabled").value(true))
                .andExpect(jsonPath("$.listViewMode").value("TABLE"))
                .andExpect(jsonPath("$.listSortMode").value("NEWEST"))
                .andExpect(jsonPath("$.themeMode").value("SYSTEM"));

        mockMvc.perform(patch("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dateDisplayFormat": "KR",
                                  "analysisCompleteNotificationEnabled": false,
                                  "listViewMode": "CARD",
                                  "listSortMode": "STATUS",
                                  "themeMode": "DARK"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateDisplayFormat").value("KR"))
                .andExpect(jsonPath("$.analysisCompleteNotificationEnabled").value(false))
                .andExpect(jsonPath("$.listViewMode").value("CARD"))
                .andExpect(jsonPath("$.listSortMode").value("STATUS"))
                .andExpect(jsonPath("$.themeMode").value("DARK"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.themeMode").value("DARK"))
                .andExpect(jsonPath("$.darkMode").value(true));
    }

    @Test
    void notifications_listAndMarkRead() throws Exception {
        notificationService.notifyAnalysisCompleted(
                testUser.getUserId(),
                evidence.getEvidenceId(),
                evidence.getFileName()
        );

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications", hasSize(1)))
                .andExpect(jsonPath("$.notifications[0].type").value("ANALYSIS_COMPLETED"))
                .andExpect(jsonPath("$.unreadCount").value(1));

        Long notificationId = notificationRepository.findAll().get(0).getNotificationId();

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void notifications_markAllAsRead() throws Exception {
        notificationService.notifyAnalysisCompleted(
                testUser.getUserId(),
                evidence.getEvidenceId(),
                evidence.getFileName()
        );
        notificationService.notifyAnalysisFailed(
                testUser.getUserId(),
                evidence.getEvidenceId(),
                evidence.getFileName()
        );

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(2));

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void compare_listOriginalsAndFileInfo() throws Exception {
        mockMvc.perform(get("/api/v1/compare/originals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].evidenceId").value(evidence.getEvidenceId()))
                .andExpect(jsonPath("$.content[0].fileName").value("sample.mp4"));

        mockMvc.perform(get("/api/v1/compare/originals/" + evidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256").value(evidence.getOriginalHashValue()))
                .andExpect(jsonPath("$.fileSize").value(12));
    }

    @Test
    void compare_verifyAndGetResult() throws Exception {
        byte[] content = "fake-video-content".getBytes();
        String hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        MockMultipartFile sameHashFile = new MockMultipartFile(
                "file",
                "candidate.mp4",
                "video/mp4",
                content
        );

        mockMvc.perform(multipart("/api/v1/compare/verify")
                        .file(sameHashFile)
                        .param("evidenceId", String.valueOf(evidence.getEvidenceId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("TAMPERED"))
                .andExpect(jsonPath("$.summary.matchCount").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.signature.originalStatus").value("UNSIGNED"))
                .andExpect(jsonPath("$.signature.candidateStatus").value("UNSIGNED"))
                .andExpect(jsonPath("$.blockchain.status").value("NOT_ANCHORED"));

        Long compareId = compareVerificationRepository.findAll().get(0).getCompareId();

        mockMvc.perform(get("/api/v1/compare/" + compareId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compareId").value(compareId))
                .andExpect(jsonPath("$.signature.originalStatus").value("UNSIGNED"))
                .andExpect(jsonPath("$.blockchain.status").value("NOT_ANCHORED"));

        mockMvc.perform(get("/api/v1/compare/" + compareId + "/candidate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("candidate.mp4"))
                .andExpect(jsonPath("$.compareId").value(compareId));

        mockMvc.perform(get("/api/v1/compare/" + compareId + "/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_NOT_APPROVED"));

        approveCase();

        mockMvc.perform(get("/api/v1/compare/" + compareId + "/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("X-Report-Hash", notNullValue(String.class)))
                .andExpect(header().string("X-Report-Status", "ISSUED"));
    }

    @Test
    void compare_verifyRegisteredEvidence() throws Exception {
        Evidence candidate = evidenceRepository.save(Evidence.builder()
                .uploaderId(testUser.getUserId())
                .caseName("비교 테스트")
                .fileName("registered-candidate.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(24L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("def456abc1237890def456abc1237890def456abc1237890def456abc1237890")
                .originalStoragePath("s3://bucket/registered-candidate.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/compare/verify-registered")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalEvidenceId": %d,
                                  "candidateEvidenceId": %d
                                }
                                """.formatted(evidence.getEvidenceId(), candidate.getEvidenceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalEvidenceId").value(evidence.getEvidenceId()))
                .andExpect(jsonPath("$.candidateFileName").value("registered-candidate.mp4"))
                .andExpect(jsonPath("$.verdict").value("TAMPERED"));

        assertThat(compareVerificationRepository.count()).isEqualTo(1);
    }

    @Test
    void compare_cancel_returnsNoContent() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/compare/cancel")
                        .param("requestId", "client-token-123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void evidenceAnalysisReport_pdf() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(testUser.getUserId());
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setProgressPercent(100);
        request.setRequestedAt(LocalDateTime.now());
        request.setStartedAt(LocalDateTime.now());
        request.setCompletedAt(LocalDateTime.now());
        request = analysisRequestRepository.save(request);

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(50.0);
        result.setConfidenceScore(0.8);
        result.setRiskLevel(RiskLevel.MEDIUM);
        result.setSummary("test summary");
        result.setAnalyzedAt(LocalDateTime.now());
        result = analysisResultRepository.save(result);

        AnalysisModuleResult timeline = new AnalysisModuleResult();
        timeline.setAnalysisResultId(result.getAnalysisResultId());
        timeline.setModuleName("video_timeline");
        timeline.setDetected(true);
        timeline.setScore(0.0);
        timeline.setConfidence(0.8);
        timeline.setModelName("ForenShield-DF");
        timeline.setModelVersion("1.2.0");
        timeline.setDetailsJson("""
                {
                  "analysisReasons":["Deepfake face mismatch detected"],
                  "frameRisks":[{"frameIndex":300,"timestampSec":12.0,"riskScore":0.82}],
                  "suspiciousSegments":[{"startTime":12.0,"endTime":15.0,"maxRiskScore":0.82,"reason":"high risk"}],
                  "moduleTimelines":[{
                    "module":"temporal",
                    "modelName":"TimeSFormer",
                    "modelVersion":"1.2.0",
                    "videoScore":0.72,
                    "threshold":0.6,
                    "detected":true,
                    "frameRisks":[],
                    "clipRisks":[{"clipIndex":3,"startFrameIndex":288,"endFrameIndex":360,"startTimeSec":12.0,"endTimeSec":15.0,"riskScore":0.82}],
                    "pairRisks":[],
                    "suspiciousSegments":[{"startTime":12.0,"endTime":15.0,"maxRiskScore":0.82,"reason":"temporal high risk"}]
                  }],
                  "representativeFrames":[{"timeSec":12.4,"timestamp":"00:12","frameNumber":310,"score":0.82,"imageUrl":"s3://bucket/frame-310.jpg"}]
                }
                """);
        timeline.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.save(timeline);

        MvcResult previewResult = mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/reports/pdf")
                        .param("preview", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("X-Report-Preview", "true"))
                .andReturn();

        PdfReader previewPdf = new PdfReader(previewResult.getResponse().getContentAsByteArray());
        try {
            String previewText = new PdfTextExtractor(previewPdf).getTextFromPage(5);
            assertThat(previewText)
                    .contains("검증 QR과 URL은 발행 등록 후 생성된다.")
                    .contains("미발행 · 미리보기")
                    .contains("미리보기 - 검증 불가")
                    .doesNotContain("vrf_");
        } finally {
            previewPdf.close();
        }

        Report previewReport = reportRepository.findTopByEvidenceIdOrderByCreatedAtDesc(evidence.getEvidenceId())
                .orElseThrow();
        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(previewReport.getStoragePath()));

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/reports/pdf")
                        .param("preview", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("X-Report-Preview", "true"));

        previewReport = reportRepository.findTopByEvidenceIdOrderByCreatedAtDesc(evidence.getEvidenceId())
                .orElseThrow();
        assertThat(previewReport.getPublicationStatus()).isEqualTo(ReportPublicationStatus.DRAFT);
        assertThat(previewReport.getVerificationToken()).isNull();

        previewReport.setVerificationToken("vrf_legacy_unissued_report");
        previewReport.setVerificationCode("VF-UNIS-SUED");
        reportRepository.save(previewReport);

        mockMvc.perform(get("/api/v1/public/reports/verify")
                        .param("token", "vrf_legacy_unissued_report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("아직 발행되지 않은 보고서입니다. 검토 승인과 발행 등록이 완료된 후 다시 확인해 주세요."));

        mockMvc.perform(post("/api/v1/public/reports/verify-file-hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "vrf_legacy_unissued_report",
                                  "fileHash": "%s"
                                }
                                """.formatted(previewReport.getReportHash())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_NOT_ISSUED"));

        mockMvc.perform(post("/api/v1/reports/" + previewReport.getReportId() + "/public-access")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_NOT_ISSUED"));

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_NOT_APPROVED"));

        approveCase();

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("X-Report-Hash", notNullValue(String.class)))
                .andExpect(header().string("X-Report-Status", "ISSUED"))
                .andExpect(header().string("X-Report-Version", "1"));

        assertThat(reportRepository.findByEvidenceIdOrderByCreatedAtDesc(evidence.getEvidenceId())).hasSize(1);
        Report report = reportRepository.findTopByEvidenceIdOrderByCreatedAtDesc(evidence.getEvidenceId())
                .orElseThrow();
        assertThat(report.getPublicationStatus()).isEqualTo(ReportPublicationStatus.ISSUED);
        assertThat(report.getVerificationToken()).isNotBlank();
        var publicationSnapshot = reportPublicationSnapshotRepository.findByReportId(report.getReportId())
                .orElseThrow();
        assertThat(publicationSnapshot.getSchemaVersion()).isEqualTo("1.1");
        assertThat(publicationSnapshot.getPdfTemplateVersion()).isEqualTo("analysis-5p-v3");
        assertThat(publicationSnapshot.getSnapshotHash()).matches("[0-9a-f]{64}");
        assertThat(publicationSnapshot.getReportInputJson()).contains("Risk Level");
        String immutableInputJson = publicationSnapshot.getReportInputJson();
        reportPublicationSnapshotService.createIfAbsent(report, java.util.List.of("Risk Level: LOW"));
        assertThat(reportPublicationSnapshotRepository.findByReportId(report.getReportId()).orElseThrow()
                .getReportInputJson()).isEqualTo(immutableInputJson);
        PdfReader issuedPdf = new PdfReader(Files.readAllBytes(Path.of(report.getStoragePath())));
        try {
            assertThat(issuedPdf.getNumberOfPages()).isEqualTo(5);
            assertThat(new PdfTextExtractor(issuedPdf).getTextFromPage(3))
                    .contains("시간축 및 시각적 근거")
                    .contains("TimeSFormer")
                    .contains("00:12.00 - 00:15.00")
                    .contains("temporal high risk")
                    .contains("대표 프레임: 별첨 없음");
        } finally {
            issuedPdf.close();
        }
        String reportHash = report.getReportHash();

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/reports/verify")
                        .param("reportHash", reportHash)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.reportHash").value(reportHash));

        mockMvc.perform(get("/api/v1/public/reports/verify")
                        .param("token", report.getVerificationToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.reportNo").value(report.getReportNo()))
                .andExpect(jsonPath("$.verificationCode").value(report.getVerificationCode()))
                .andExpect(jsonPath("$.reportType").value("ANALYSIS"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.publicationStatus").value("ISSUED"))
                .andExpect(jsonPath("$.issuedAt", notNullValue(String.class)))
                .andExpect(jsonPath("$.queriedAt", notNullValue(String.class)))
                .andExpect(jsonPath("$.pdfSignatureApplied").value(false))
                .andExpect(jsonPath("$.evidenceManifestSignatureStatus", notNullValue(String.class)))
                .andExpect(jsonPath("$.analysisVerdict").value("판정 불가"))
                .andExpect(jsonPath("$.analysisCompletedAt", notNullValue(String.class)))
                .andExpect(jsonPath("$.snapshotSchemaVersion").value("1.1"))
                .andExpect(jsonPath("$.pdfTemplateVersion").value("analysis-5p-v3"))
                .andExpect(jsonPath("$.hashMatched").value(true))
                .andExpect(jsonPath("$.reportHash").value(reportHash));

        mockMvc.perform(get("/api/v1/public/reports/verify")
                        .param("code", report.getVerificationCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.reportNo").value(report.getReportNo()))
                .andExpect(jsonPath("$.reportHash").value(reportHash));

        mockMvc.perform(post("/api/v1/public/reports/verify-file-hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "fileHash": "%s"
                                }
                                """.formatted(report.getVerificationToken(), reportHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCH"))
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.storedFileIntact").value(true))
                .andExpect(jsonPath("$.reportNo").value(report.getReportNo()))
                .andExpect(jsonPath("$.submittedHash").value(reportHash))
                .andExpect(jsonPath("$.registeredHash").value(reportHash));

        String differentPdfHash = "0".repeat(64);
        mockMvc.perform(post("/api/v1/public/reports/verify-file-hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "fileHash": "%s"
                                }
                                """.formatted(report.getVerificationToken(), differentPdfHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISMATCH"))
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.submittedHash").value(differentPdfHash))
                .andExpect(jsonPath("$.registeredHash").value(reportHash));

        mockMvc.perform(post("/api/v1/reports/" + report.getReportId() + "/public-access")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportNo").value(report.getReportNo()))
                .andExpect(jsonPath("$.accessCode", notNullValue(String.class)))
                .andExpect(jsonPath("$.publicViewUrl", notNullValue(String.class)));

        Report reportWithAccess = reportRepository.findById(report.getReportId()).orElseThrow();
        String accessCode = reportWithAccess.getPublicAccessCode();

        mockMvc.perform(get("/api/v1/public/reports/view")
                        .param("code", accessCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportNo").value(report.getReportNo()))
                .andExpect(jsonPath("$.downloadPath", notNullValue(String.class)));

        mockMvc.perform(get("/api/v1/public/reports/view/pdf")
                        .param("code", accessCode))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE));

        mockMvc.perform(get("/api/v1/public/reports/verify")
                        .param("token", "missing-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REPORT_VERIFICATION_NOT_FOUND"));
    }

    private void approveCase() {
        CaseProfile profile = caseProfileRepository.findByUploaderIdAndCaseKey(
                        testUser.getUserId(),
                        "비교 테스트"
                )
                .orElseGet(() -> new CaseProfile(
                        testUser.getUserId(),
                        "비교 테스트",
                        evidence.getEvidenceId()
                ));
        profile.approveReview();
        caseProfileRepository.save(profile);
    }

    @Test
    void evidenceDetail_includesModuleModelFields() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(testUser.getUserId());
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setProgressPercent(100);
        request.setRequestedAt(LocalDateTime.now());
        request.setCompletedAt(LocalDateTime.now());
        request = analysisRequestRepository.save(request);

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(72.0);
        result.setConfidenceScore(0.91);
        result.setRiskLevel(RiskLevel.HIGH);
        result.setSummary("deepfake suspected");
        result.setAnalyzedAt(LocalDateTime.now());
        result = analysisResultRepository.save(result);

        AnalysisModuleResult module = new AnalysisModuleResult();
        module.setAnalysisResultId(result.getAnalysisResultId());
        module.setModuleName("deepfake");
        module.setDetected(true);
        module.setScore(0.92);
        module.setConfidence(0.88);
        module.setModelName("ForenShield-DF");
        module.setModelVersion("1.2.0");
        module.setDetailsJson("{\"deepfakeScore\":0.92}");
        module.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.save(module);

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/detail")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisInfo.moduleResults[0].modelName").value("ForenShield-DF"))
                .andExpect(jsonPath("$.analysisInfo.moduleResults[0].modelVersion").value("1.2.0"))
                .andExpect(jsonPath("$.analysisInfo.moduleResults[0].confidence").value(0.88))
                .andExpect(jsonPath("$.analysisInfo.modelScores[0].moduleName").value("deepfake"))
                .andExpect(jsonPath("$.analysisInfo.modelScores[0].score").value(0.92));
    }

    @Test
    void evidenceDetail_includesVisualizationFields() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(testUser.getUserId());
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setProgressPercent(100);
        request.setRequestedAt(LocalDateTime.now());
        request.setCompletedAt(LocalDateTime.now());
        request = analysisRequestRepository.save(request);

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(72.0);
        result.setConfidenceScore(0.91);
        result.setRiskLevel(RiskLevel.HIGH);
        result.setSummary("deepfake suspected");
        result.setAnalyzedAt(LocalDateTime.now());
        result = analysisResultRepository.save(result);

        AnalysisModuleResult timeline = new AnalysisModuleResult();
        timeline.setAnalysisResultId(result.getAnalysisResultId());
        timeline.setModuleName("video_timeline");
        timeline.setDetected(true);
        timeline.setScore(0.0);
        timeline.setConfidence(0.91);
        timeline.setModelName("ForenShield-DF");
        timeline.setModelVersion("1.2.0");
        timeline.setDetailsJson("""
                {
                  "frameRisks":[{"frameIndex":0,"timestampSec":0.0,"riskScore":0.82}],
                  "suspiciousSegments":[{"startTime":12.0,"endTime":15.0,"maxRiskScore":0.82,"reason":"high risk"}],
                  "clipRisks":[{"clipIndex":0,"startFrameIndex":0,"endFrameIndex":8,"startTimeSec":0.0,"endTimeSec":0.375,"riskScore":0.55}],
                  "pairRisks":[{"pairIndex":0,"frameIndexA":0,"frameIndexB":1,"timestampSec":0.04,"riskScore":0.44,"motionMagnitude":1.1}],
                  "temporalSuspiciousSegments":[{"startTime":0.0,"endTime":0.5,"maxRiskScore":0.55,"reason":"ts segment"}],
                  "opticalSuspiciousSegments":[{"startTime":0.0,"endTime":0.1,"maxRiskScore":0.44,"reason":"gmf segment"}],
                  "representativeFrames":[{"timeSec":0.4,"timestamp":"00:00","frameNumber":10,"score":0.82,"imageUrl":"https://cdn.example/frame.jpg"}],
                  "overlayVideoUrl":"https://cdn.example/overlay.mp4",
                  "analysisReasons":["Deepfake face mismatch detected"]
                }
                """);
        timeline.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.save(timeline);

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/detail")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisInfo.frameRisks[0].riskScore").value(0.82))
                .andExpect(jsonPath("$.analysisInfo.suspiciousSegments[0].startTime").value(12.0))
                .andExpect(jsonPath("$.analysisInfo.clipRisks[0].riskScore").value(0.55))
                .andExpect(jsonPath("$.analysisInfo.pairRisks[0].motionMagnitude").value(1.1))
                .andExpect(jsonPath("$.analysisInfo.temporalSuspiciousSegments[0].reason").value("ts segment"))
                .andExpect(jsonPath("$.analysisInfo.opticalSuspiciousSegments[0].reason").value("gmf segment"))
                .andExpect(jsonPath("$.analysisInfo.representativeFrames[0].frameNumber").value(10))
                .andExpect(jsonPath("$.analysisInfo.representativeFrames[0].imageUrl").value("https://cdn.example/frame.jpg"))
                .andExpect(jsonPath("$.analysisInfo.overlayVideoUrl").value("https://cdn.example/overlay.mp4"))
                .andExpect(jsonPath("$.analysisInfo.evidenceItems[0]").value("Deepfake face mismatch detected"));
    }

    @Test
    void evidenceBlockchain_status() throws Exception {
        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash(evidence.getOriginalHashValue());
        anchor.setEvidenceId(evidence.getEvidenceId());
        anchor.setCreatedBy(testUser.getUserId());
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setTransactionHash("0xabc123");
        anchor.setNetwork("local-simulated");
        anchor.setAnchoredAt(LocalDateTime.now());
        anchor.setCreatedAt(LocalDateTime.now());
        blockchainAnchorRepository.save(anchor);

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/blockchain")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(evidence.getEvidenceId()))
                .andExpect(jsonPath("$.evidenceHashAnchor.status").value("ANCHORED"))
                .andExpect(jsonPath("$.evidenceHashAnchor.transactionHash").value("0xabc123"))
                .andExpect(jsonPath("$.evidenceHashAnchor.transactionExplorerUrl")
                        .value("https://explorer.test/tx/0xabc123"));
    }

    @Test
    void evidenceDetail_blockchainIntegrityAndExplorerUrl() throws Exception {
        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash(evidence.getOriginalHashValue());
        anchor.setEvidenceId(evidence.getEvidenceId());
        anchor.setCreatedBy(testUser.getUserId());
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setTransactionHash("0xabc123");
        anchor.setNetwork("local-simulated");
        anchor.setAnchoredAt(LocalDateTime.now());
        anchor.setCreatedAt(LocalDateTime.now());
        blockchainAnchorRepository.save(anchor);

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/detail")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(StepUpTestSupport.STEP_UP_HEADER, stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockchainInfo.status").value("ANCHORED"))
                .andExpect(jsonPath("$.blockchainInfo.hashValid").value(true))
                .andExpect(jsonPath("$.blockchainInfo.transactionExplorerUrl")
                        .value("https://explorer.test/tx/0xabc123"));
    }

    @Test
    void compare_verifyUsesAnchoredBlockchainHash() throws Exception {
        byte[] content = "fake-video-content".getBytes(StandardCharsets.UTF_8);
        Path tempFile = Files.createTempFile("compare-candidate", ".mp4");
        Files.write(tempFile, content);
        String candidateHash = hashService.generateSha256(tempFile);
        Files.deleteIfExists(tempFile);

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash(candidateHash);
        anchor.setEvidenceId(evidence.getEvidenceId());
        anchor.setCreatedBy(testUser.getUserId());
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setTransactionHash("0xcompare");
        anchor.setNetwork("local-simulated");
        anchor.setAnchoredAt(LocalDateTime.now());
        anchor.setCreatedAt(LocalDateTime.now());
        blockchainAnchorRepository.save(anchor);

        MockMultipartFile sameHashFile = new MockMultipartFile(
                "file",
                "candidate.mp4",
                "video/mp4",
                content
        );

        mockMvc.perform(multipart("/api/v1/compare/verify")
                        .file(sameHashFile)
                        .param("evidenceId", String.valueOf(evidence.getEvidenceId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.itemKey=='BLOCKCHAIN_HASH')].result").value("MATCH"))
                .andExpect(jsonPath("$.items[?(@.itemKey=='BLOCKCHAIN_HASH')].originalValue").value(candidateHash));
    }
}
