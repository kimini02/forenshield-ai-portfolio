package com.example.demo.performance.c3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.CaseReviewStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.domain.enums.ReportPublicationStatus;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportPublicationSnapshotRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.blockchain.client.HttpBlockchainAnchorClient;
import com.example.demo.service.report.ReportIssuePersistenceService;
import com.example.demo.service.report.ReportIssueTaskProcessor;
import com.example.demo.service.report.ReportIssueTaskRecoveryService;
import com.example.demo.service.report.ReportIssueTaskStateService;
import com.example.demo.support.JwtTestSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.maximum-pool-size=5",
        "blockchain.anchor.enabled=true",
        "blockchain.anchor.mode=http",
        "blockchain.anchor.network=c3-local-stub",
        "blockchain.anchor.scheduler-enabled=false",
        "report.issue.worker.enabled=false",
        "report.issue.worker.retry-delay-ms=0",
        "report.issue.worker.processing-timeout-ms=1",
        "analysis.worker.stale-reaper-enabled=false",
        "hls.packaging.enabled=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
class ApprovalTransactionAfterMeasurementTest {

    private static final String PASSWORD = "c3-after-test-password";
    private static final String RESULT_PREFIX = "C3_AFTER_RESULT";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("forenshield_c3_after")
            .withUsername("forenshield")
            .withPassword("forenshield")
            .withInitScript("db/test/postgresql-domains.sql");

    private static final Path UPLOAD_ROOT = createUploadRoot();
    private static final BlockchainStub BLOCKCHAIN_STUB = BlockchainStub.start();
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @DynamicPropertySource
    static void configureEnvironment(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?ApplicationName=c3-after");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("blockchain.anchor.http-url", BLOCKCHAIN_STUB::url);
        registry.add("file.upload-dir", UPLOAD_ROOT::toString);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSource dataSource;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private EvidenceRepository evidenceRepository;
    @Autowired private CaseProfileRepository caseProfileRepository;
    @Autowired private AnalysisRequestRepository analysisRequestRepository;
    @Autowired private AnalysisResultRepository analysisResultRepository;
    @Autowired private AnalysisModuleResultRepository analysisModuleResultRepository;
    @Autowired private ReportIssueTaskRepository taskRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private ReportPublicationSnapshotRepository snapshotRepository;
    @Autowired private BlockchainAnchorRepository anchorRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CustodyLogRepository custodyLogRepository;
    @Autowired private ReportIssueTaskStateService taskStateService;
    @Autowired private ReportIssueTaskProcessor processor;
    @Autowired private ReportIssueTaskRecoveryService recoveryService;

    @SpyBean private ReportIssuePersistenceService persistenceService;
    @SpyBean private BlockchainAnchorService blockchainAnchorService;
    @SpyBean private HttpBlockchainAnchorClient httpBlockchainAnchorClient;

    @BeforeEach
    void cleanDatabaseAndStub() {
        reset(persistenceService, blockchainAnchorService, httpBlockchainAnchorClient);
        notificationRepository.deleteAll();
        custodyLogRepository.deleteAll();
        anchorRepository.deleteAll();
        snapshotRepository.deleteAll();
        reportRepository.deleteAll();
        taskRepository.deleteAll();
        caseProfileRepository.deleteAll();
        analysisModuleResultRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
        BLOCKCHAIN_STUB.reset(0L);
    }

    @AfterEach
    void restoreSpies() {
        reset(persistenceService, blockchainAnchorService, httpBlockchainAnchorClient);
    }

    @AfterAll
    static void stopStub() {
        BLOCKCHAIN_STUB.stop();
    }

    @Test
    @Order(1)
    void measuresApprovalAndWorkerSeparatelyForBlockchainDelays() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        printResult("ENV",
                "postgresImage=" + POSTGRES.getDockerImageName(),
                "postgresVersion=" + postgresVersion(),
                "javaVersion=" + System.getProperty("java.version"),
                "hikariMaxPool=" + hikari.getMaximumPoolSize(),
                "schedulerEnabled=false",
                "pdfRoot=isolated-temp-directory");

        for (long delayMs : List.of(0L, 1_000L, 5_000L)) {
            for (int run = 1; run <= 3; run++) {
                Fixture fixture = createFixture("delay-" + delayMs + "-" + run);
                BLOCKCHAIN_STUB.reset(delayMs);

                ApprovalMeasurement approval = invokeApproval(fixture);
                ReportIssueTask pending = findOnlyTask(fixture.caseProfileId());
                assertThat(pending.getStatus()).isEqualTo(ReportIssueTaskStatus.PENDING);
                assertThat(reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()))
                        .isEmpty();
                assertThat(BLOCKCHAIN_STUB.calls()).isEmpty();

                HttpProbe httpProbe = observeRealHttpClient(hikari);
                WorkerMeasurement worker = invokeWorker(pending.getReportIssueTaskId(), httpProbe);

                ReportIssueTask completed = taskRepository.findById(pending.getReportIssueTaskId()).orElseThrow();
                Report report = reportRepository
                        .findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()).orElseThrow();
                BlockchainAnchor anchor = anchorRepository
                        .findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                                report.getReportId(), BlockchainAnchorType.REPORT_HASH).orElseThrow();
                assertThat(completed.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
                assertThat(report.getPublicationStatus()).isEqualTo(ReportPublicationStatus.ISSUED);
                assertThat(anchor.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
                assertThat(BLOCKCHAIN_STUB.calls()).hasSize(1);

                printResult("DELAY",
                        "delayMs=" + delayMs,
                        "run=" + run,
                        "approvalApiMs=" + format(approval.apiLatencyMs()),
                        "approvalTxMs=" + approval.transactionDurationMs(),
                        "approvalMaxXactAgeMs=" + format(approval.maxTransactionAgeMs()),
                        "approvalHikariMax=" + approval.hikariMax(),
                        "workerE2eMs=" + format(worker.e2eMs()),
                        "httpMs=" + format(worker.httpMs()),
                        "workerMaxTxMs=" + worker.maxTransactionDurationMs(),
                        "workerMaxXactAgeMs=" + format(worker.maxTransactionAgeMs()),
                        "springTxAtHttp=" + httpProbe.springTransactionActive(),
                        "httpHikariBefore=" + httpProbe.hikariBefore(),
                        "httpHikariMax=" + worker.maxHikariDuringHttp(),
                        "httpHikariAfter=" + httpProbe.hikariAfter(),
                        "httpMaxPgXactAgeMs=" + format(worker.maxPgTransactionAgeDuringHttpMs()),
                        "stubCalls=" + BLOCKCHAIN_STUB.calls().size(),
                        "txWindows=" + worker.transactionSummary());
            }
        }
    }

    @Test
    @Order(2)
    void reportPersistenceFailureLeavesDurableTaskAndRetryCompletes() throws Exception {
        Fixture fixture = createFixture("pdf-retry");
        invokeApproval(fixture);
        ReportIssueTask task = findOnlyTask(fixture.caseProfileId());
        Long taskId = task.getReportIssueTaskId();

        assertThat(taskStateService.claimBatch()).contains(taskId);
        doThrow(new ForcedFailure("forced report persistence failure after PDF write"))
                .when(persistenceService).persistIssuedReport(any(), any());
        processor.process(taskId);

        ReportIssueTask failedAttempt = taskRepository.findById(taskId).orElseThrow();
        Path firstPdf = Path.of(failedAttempt.getArtifactPath());
        String firstPath = firstPdf.toString();
        long firstBytes = Files.size(firstPdf);
        String firstSha = sha256(firstPdf);
        assertThat(caseProfileRepository.findById(fixture.caseProfileId()).orElseThrow().getReviewStatus())
                .isEqualTo(CaseReviewStatus.REPORT_APPROVED);
        assertThat(failedAttempt.getStatus()).isEqualTo(ReportIssueTaskStatus.PENDING);
        assertThat(failedAttempt.getAttemptCount()).isEqualTo(1);
        assertThat(failedAttempt.getLastError()).contains("forced report persistence failure", "artifactPath=");
        assertThat(failedAttempt.getNextRetryAt()).isNotNull();
        assertThat(reportRepository.count()).isZero();
        assertThat(snapshotRepository.count()).isZero();
        assertThat(anchorRepository.count()).isZero();
        assertThat(Files.isRegularFile(firstPdf)).isTrue();

        printResult("PDF_FAILURE",
                "profile=REPORT_APPROVED",
                "taskStatus=PENDING",
                "attemptCount=1",
                "lastErrorPresent=true",
                "nextRetryAtPresent=true",
                "reportCount=0",
                "snapshotCount=0",
                "anchorCount=0",
                "pdfExists=true",
                "pdfBytes=" + firstBytes,
                "pdfSha256=" + firstSha);

        reset(persistenceService);
        BLOCKCHAIN_STUB.reset(0L);
        assertThat(taskStateService.claimBatch()).contains(taskId);
        processor.process(taskId);

        ReportIssueTask completed = taskRepository.findById(taskId).orElseThrow();
        Report report = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()).orElseThrow();
        List<Path> pdfFiles = findPdfFiles(fixture.evidenceId());
        assertThat(completed.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
        assertThat(completed.getAttemptCount()).isEqualTo(2);
        assertThat(report.isIssued()).isTrue();
        assertThat(pdfFiles).hasSize(1);
        assertThat(report.getStoragePath()).isEqualTo(firstPath);

        printResult("PDF_RETRY",
                "transition=PENDING-PROCESSING-PENDING-PROCESSING-COMPLETED",
                "attemptCount=2",
                "report=ISSUED",
                "pdfExists=true",
                "samePath=true",
                "pdfFileCount=" + pdfFiles.size(),
                "finalPdfBytes=" + Files.size(pdfFiles.get(0)),
                "finalPdfSha256=" + sha256(pdfFiles.get(0)),
                "stubCalls=" + BLOCKCHAIN_STUB.calls().size());
    }

    @Test
    @Order(3)
    void anchorResultPersistenceFailureIsRecordedAsOutcomeUnknown() throws Exception {
        Fixture fixture = createFixture("anchor-result-failure");
        invokeApproval(fixture);
        ReportIssueTask task = findOnlyTask(fixture.caseProfileId());
        assertThat(taskStateService.claimBatch()).contains(task.getReportIssueTaskId());
        BLOCKCHAIN_STUB.reset(0L);
        doThrow(new ForcedFailure("forced ANCHORED result persistence failure"))
                .when(blockchainAnchorService).recordPreparedReportAnchorResult(anyLong(), any());

        processor.process(task.getReportIssueTaskId());

        ReportIssueTask completed = taskRepository.findById(task.getReportIssueTaskId()).orElseThrow();
        Report report = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()).orElseThrow();
        BlockchainAnchor anchor = anchorRepository
                .findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                        report.getReportId(), BlockchainAnchorType.REPORT_HASH).orElseThrow();
        Path pdf = Path.of(report.getStoragePath());
        assertThat(caseProfileRepository.findById(fixture.caseProfileId()).orElseThrow().getReviewStatus())
                .isEqualTo(CaseReviewStatus.REPORT_APPROVED);
        assertThat(report.isIssued()).isTrue();
        assertThat(completed.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
        assertThat(anchor.getStatus()).isEqualTo(BlockchainAnchorStatus.FAILED);
        assertThat(anchor.getErrorCode()).isEqualTo("ANCHOR_OUTCOME_UNKNOWN");
        assertThat(BLOCKCHAIN_STUB.calls()).hasSize(1);

        printResult("ANCHOR_RESULT_FAILURE",
                "profile=REPORT_APPROVED",
                "report=ISSUED",
                "taskStatus=COMPLETED",
                "anchorStatus=FAILED",
                "errorCode=" + anchor.getErrorCode(),
                "manualReconciliation=" + anchor.getErrorMessage().contains("Manual reconciliation required"),
                "stubCalls=" + BLOCKCHAIN_STUB.calls().size(),
                "pdfExists=" + Files.isRegularFile(pdf),
                "pdfSha256=" + sha256(pdf));
    }

    @Test
    @Order(4)
    void staleRecoveryDoesNotResubmitAmbiguousBlockchainRequest() throws Exception {
        Fixture fixture = createFixture("anchor-stale-recovery");
        invokeApproval(fixture);
        ReportIssueTask task = findOnlyTask(fixture.caseProfileId());
        Long taskId = task.getReportIssueTaskId();
        assertThat(taskStateService.claimBatch()).contains(taskId);
        BLOCKCHAIN_STUB.reset(0L);
        doThrow(new ForcedFailure("forced ANCHORED result persistence failure"))
                .when(blockchainAnchorService).recordPreparedReportAnchorResult(anyLong(), any());
        doThrow(new ForcedFailure("simulated process stop before outcome-unknown persistence"))
                .when(blockchainAnchorService).markReportAnchorOutcomeUnknown(anyLong(), any());

        assertThatThrownBy(() -> processor.process(taskId)).isInstanceOf(ForcedFailure.class);

        Report report = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()).orElseThrow();
        BlockchainAnchor pendingAnchor = anchorRepository
                .findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                        report.getReportId(), BlockchainAnchorType.REPORT_HASH).orElseThrow();
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(ReportIssueTaskStatus.PROCESSING);
        assertThat(pendingAnchor.getStatus()).isEqualTo(BlockchainAnchorStatus.PENDING);
        assertThat(BLOCKCHAIN_STUB.calls()).hasSize(1);

        reset(blockchainAnchorService);
        Thread.sleep(10L);
        int recovered = recoveryService.recoverStaleTasks();

        ReportIssueTask recoveredTask = taskRepository.findById(taskId).orElseThrow();
        BlockchainAnchor recoveredAnchor = anchorRepository.findById(pendingAnchor.getAnchorId()).orElseThrow();
        assertThat(recovered).isEqualTo(1);
        assertThat(recoveredTask.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
        assertThat(recoveredAnchor.getStatus()).isEqualTo(BlockchainAnchorStatus.FAILED);
        assertThat(recoveredAnchor.getErrorCode()).isEqualTo("ANCHOR_OUTCOME_UNKNOWN");
        assertThat(recoveredAnchor.getErrorMessage()).contains("Manual reconciliation required");
        assertThat(BLOCKCHAIN_STUB.calls()).hasSize(1);

        printResult("ANCHOR_STALE_RECOVERY",
                "beforeTask=PROCESSING",
                "beforeAnchor=PENDING",
                "afterTask=COMPLETED",
                "afterAnchor=FAILED",
                "errorCode=" + recoveredAnchor.getErrorCode(),
                "manualReconciliation=true",
                "stubCallsBefore=1",
                "stubCallsAfter=" + BLOCKCHAIN_STUB.calls().size(),
                "httpResubmitted=false");
    }

    private Fixture createFixture(String label) throws Exception {
        int sequence = FIXTURE_SEQUENCE.incrementAndGet();
        String suffix = label.replaceAll("[^a-zA-Z0-9]", "-") + "-" + sequence;
        String investigatorLogin = "c3-after-inv-" + suffix;
        String reviewerLogin = "c3-after-rev-" + suffix;
        String caseKey = "C3-AFTER-CASE-" + suffix;

        User investigator = userRepository.saveAndFlush(User.builder()
                .loginId(investigatorLogin)
                .email(investigatorLogin + "@test.local")
                .password(passwordEncoder.encode(PASSWORD))
                .name("C3 after investigator")
                .organizationType(OrgType.POLICE)
                .department("C3 isolated")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
        User reviewer = userRepository.saveAndFlush(User.builder()
                .loginId(reviewerLogin)
                .email(reviewerLogin + "@test.local")
                .password(passwordEncoder.encode(PASSWORD))
                .name("C3 after reviewer")
                .organizationType(OrgType.POLICE)
                .department("C3 isolated")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        Evidence evidence = evidenceRepository.saveAndFlush(Evidence.builder()
                .uploaderId(investigator.getUserId())
                .caseName(caseKey)
                .caseNumber(caseKey)
                .fileName("c3-after-" + suffix + ".mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue(String.format("%064x", sequence))
                .originalStoragePath("c3-after-fixture/" + suffix + ".mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(investigator.getUserId());
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setProgressPercent(100);
        request.setRequestedAt(LocalDateTime.now().minusSeconds(2));
        request.setStartedAt(LocalDateTime.now().minusSeconds(1));
        request.setCompletedAt(LocalDateTime.now());
        request = analysisRequestRepository.saveAndFlush(request);

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(64.0);
        result.setConfidenceScore(0.88);
        result.setRiskLevel(RiskLevel.MEDIUM);
        result.setSummary("C3 isolated after fixture");
        result.setAnalyzedAt(LocalDateTime.now());
        result = analysisResultRepository.saveAndFlush(result);

        AnalysisModuleResult module = new AnalysisModuleResult();
        module.setAnalysisResultId(result.getAnalysisResultId());
        module.setFileType(FileType.VIDEO);
        module.setModuleName("deepfake");
        module.setDetected(true);
        module.setScore(0.64);
        module.setConfidence(0.88);
        module.setModelName("c3-test-model");
        module.setModelVersion("after-v1");
        module.setDetailsJson("{}");
        module.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.saveAndFlush(module);

        CaseProfile profile = new CaseProfile(investigator.getUserId(), caseKey, evidence.getEvidenceId());
        profile.assignReviewer(reviewer.getUserId());
        profile = caseProfileRepository.saveAndFlush(profile);

        String reviewerToken = JwtTestSupport.loginAndGetToken(mockMvc, reviewerLogin, PASSWORD);
        return new Fixture(caseKey, evidence.getEvidenceId(), result.getAnalysisResultId(),
                profile.getCaseProfileId(), reviewerToken);
    }

    private ApprovalMeasurement invokeApproval(Fixture fixture) throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        ActivitySampler sampler = new ActivitySampler(hikari, null);
        TransactionLogCapture logs = TransactionLogCapture.start();
        sampler.start();
        long started = System.nanoTime();
        MvcResult result;
        try {
            result = mockMvc.perform(post("/api/v1/cases/review-decision")
                            .queryParam("caseKey", fixture.caseKey())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.reviewerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"APPROVED\",\"memo\":\"C3 after measurement\"}"))
                    .andReturn();
        } finally {
            sampler.stopAndJoin();
            logs.stop();
        }
        double apiMs = nanosToMillis(System.nanoTime() - started);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(sampler.failure()).isNull();
        TransactionWindow tx = logs.findByName(
                "com.example.demo.service.evidence.CaseReviewService.recordDecision");
        return new ApprovalMeasurement(apiMs, tx.durationMs(), sampler.maxTransactionAgeMs(),
                sampler.maxHikariActive());
    }

    private WorkerMeasurement invokeWorker(Long taskId, HttpProbe httpProbe) throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        ActivitySampler sampler = new ActivitySampler(hikari, httpProbe);
        TransactionLogCapture logs = TransactionLogCapture.start();
        sampler.start();
        long started = System.nanoTime();
        try {
            assertThat(taskStateService.claimBatch()).contains(taskId);
            processor.process(taskId);
        } finally {
            sampler.stopAndJoin();
            logs.stop();
        }
        double e2eMs = nanosToMillis(System.nanoTime() - started);
        assertThat(sampler.failure()).isNull();
        List<TransactionWindow> windows = logs.windows();
        return new WorkerMeasurement(
                e2eMs,
                BLOCKCHAIN_STUB.calls().stream().mapToDouble(StubCall::durationMs).sum(),
                windows.stream().mapToLong(TransactionWindow::durationMs).max().orElse(0L),
                sampler.maxTransactionAgeMs(),
                sampler.maxPgTransactionAgeDuringHttpMs(),
                sampler.maxHikariDuringHttp(),
                summarize(windows));
    }

    private HttpProbe observeRealHttpClient(HikariDataSource hikari) {
        reset(httpBlockchainAnchorClient);
        HttpProbe probe = new HttpProbe(hikari);
        doAnswer(invocation -> {
            probe.begin();
            try {
                return invocation.callRealMethod();
            } finally {
                probe.end();
            }
        }).when(httpBlockchainAnchorClient).anchor(any());
        return probe;
    }

    private ReportIssueTask findOnlyTask(Long caseProfileId) {
        List<ReportIssueTask> tasks = taskRepository.findAll().stream()
                .filter(task -> task.getCaseProfileId().equals(caseProfileId))
                .toList();
        assertThat(tasks).hasSize(1);
        return tasks.get(0);
    }

    private List<Path> findPdfFiles(Long evidenceId) throws IOException {
        Path directory = UPLOAD_ROOT.resolve("reports").resolve("evidence").resolve(String.valueOf(evidenceId));
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".pdf")).sorted().toList();
        }
    }

    private String postgresVersion() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW server_version")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
    }

    private static String summarize(List<TransactionWindow> windows) {
        return windows.stream()
                .map(window -> shortName(window.name()) + ":" + window.durationMs() + "ms")
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
    }

    private static String shortName(String value) {
        int index = value.lastIndexOf('.');
        return index < 0 ? value : value.substring(index + 1);
    }

    private static Path createUploadRoot() {
        try {
            return Files.createTempDirectory("forenshield-c3-after-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void printResult(String scenario, String... values) {
        System.out.println(RESULT_PREFIX + "\t" + scenario + "\t" + String.join("\t", values));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record Fixture(String caseKey, Long evidenceId, Long analysisResultId,
                           Long caseProfileId, String reviewerToken) {
    }

    private record ApprovalMeasurement(double apiLatencyMs, long transactionDurationMs,
                                       double maxTransactionAgeMs, int hikariMax) {
    }

    private record WorkerMeasurement(double e2eMs, double httpMs, long maxTransactionDurationMs,
                                     double maxTransactionAgeMs, double maxPgTransactionAgeDuringHttpMs,
                                     int maxHikariDuringHttp, String transactionSummary) {
    }

    private record TransactionWindow(String name, Instant begin, Instant end, long durationMs) {
    }

    private static final class TransactionLogCapture {
        private final Logger logger;
        private final Level previousLevel;
        private final ListAppender<ILoggingEvent> appender;

        private TransactionLogCapture(Logger logger, Level previousLevel, ListAppender<ILoggingEvent> appender) {
            this.logger = logger;
            this.previousLevel = previousLevel;
            this.appender = appender;
        }

        static TransactionLogCapture start() {
            Logger logger = (Logger) LoggerFactory.getLogger(JpaTransactionManager.class);
            Level previousLevel = logger.getLevel();
            logger.setLevel(Level.DEBUG);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            return new TransactionLogCapture(logger, previousLevel, appender);
        }

        void stop() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }

        TransactionWindow findByName(String name) {
            return windows().stream()
                    .filter(window -> window.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("transaction window not found: " + name));
        }

        List<TransactionWindow> windows() {
            List<ILoggingEvent> events = new ArrayList<>(appender.list);
            List<TransactionWindow> result = new ArrayList<>();
            for (int beginIndex = 0; beginIndex < events.size(); beginIndex++) {
                String message = events.get(beginIndex).getFormattedMessage();
                String marker = "Creating new transaction with name [";
                int markerIndex = message.indexOf(marker);
                if (markerIndex < 0) {
                    continue;
                }
                int nameStart = markerIndex + marker.length();
                int nameEnd = message.indexOf(']', nameStart);
                String name = message.substring(nameStart, nameEnd);
                ILoggingEvent begin = events.get(beginIndex);
                ILoggingEvent end = null;
                for (int endIndex = beginIndex + 1; endIndex < events.size(); endIndex++) {
                    String endMessage = events.get(endIndex).getFormattedMessage();
                    if (endMessage.contains("Initiating transaction commit")
                            || endMessage.contains("Initiating transaction rollback")) {
                        end = events.get(endIndex);
                        beginIndex = endIndex;
                        break;
                    }
                }
                if (end != null) {
                    Instant beginInstant = Instant.ofEpochMilli(begin.getTimeStamp());
                    Instant endInstant = Instant.ofEpochMilli(end.getTimeStamp());
                    result.add(new TransactionWindow(name, beginInstant, endInstant,
                            endInstant.toEpochMilli() - beginInstant.toEpochMilli()));
                }
            }
            return result;
        }
    }

    private final class ActivitySampler {
        private final HikariDataSource hikari;
        private final HttpProbe httpProbe;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicLong maxTransactionAgeMicros = new AtomicLong();
        private final AtomicLong maxHttpTransactionAgeMicros = new AtomicLong();
        private final AtomicInteger maxHikariActive = new AtomicInteger();
        private final AtomicInteger maxHikariDuringHttp = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private Thread thread;

        private ActivitySampler(HikariDataSource hikari, HttpProbe httpProbe) {
            this.hikari = hikari;
            this.httpProbe = httpProbe;
        }

        void start() {
            running.set(true);
            thread = new Thread(this::sample, "c3-after-pg-activity-sampler");
            thread.setDaemon(true);
            thread.start();
        }

        private void sample() {
            String samplerUrl = POSTGRES.getJdbcUrl() + "?ApplicationName=c3-after-sampler";
            String sql = """
                    SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (clock_timestamp() - xact_start)) * 1000000), 0)
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND application_name = 'c3-after'
                      AND xact_start IS NOT NULL
                    """;
            try (Connection connection = DriverManager.getConnection(
                    samplerUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = connection.createStatement()) {
                while (running.get()) {
                    try (ResultSet resultSet = statement.executeQuery(sql)) {
                        if (resultSet.next()) {
                            long ageMicros = resultSet.getLong(1);
                            maxTransactionAgeMicros.accumulateAndGet(ageMicros, Math::max);
                            if (httpProbe != null && httpProbe.inProgress()) {
                                maxHttpTransactionAgeMicros.accumulateAndGet(ageMicros, Math::max);
                            }
                        }
                    }
                    int active = hikari.getHikariPoolMXBean().getActiveConnections();
                    maxHikariActive.accumulateAndGet(active, Math::max);
                    if (httpProbe != null && httpProbe.inProgress()) {
                        maxHikariDuringHttp.accumulateAndGet(active, Math::max);
                    }
                    Thread.sleep(5L);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }

        void stopAndJoin() throws InterruptedException {
            running.set(false);
            if (thread != null) {
                thread.join(5_000L);
            }
        }

        double maxTransactionAgeMs() {
            return maxTransactionAgeMicros.get() / 1_000.0;
        }

        double maxPgTransactionAgeDuringHttpMs() {
            return maxHttpTransactionAgeMicros.get() / 1_000.0;
        }

        int maxHikariActive() {
            return maxHikariActive.get();
        }

        int maxHikariDuringHttp() {
            return maxHikariDuringHttp.get();
        }

        Throwable failure() {
            return failure.get();
        }
    }

    private static final class HttpProbe {
        private final HikariDataSource hikari;
        private final AtomicBoolean inProgress = new AtomicBoolean();
        private volatile boolean springTransactionActive;
        private volatile int hikariBefore;
        private volatile int hikariAfter;

        private HttpProbe(HikariDataSource hikari) {
            this.hikari = hikari;
        }

        void begin() {
            springTransactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            hikariBefore = hikari.getHikariPoolMXBean().getActiveConnections();
            inProgress.set(true);
        }

        void end() {
            hikariAfter = hikari.getHikariPoolMXBean().getActiveConnections();
            inProgress.set(false);
        }

        boolean inProgress() {
            return inProgress.get();
        }

        boolean springTransactionActive() {
            return springTransactionActive;
        }

        int hikariBefore() {
            return hikariBefore;
        }

        int hikariAfter() {
            return hikariAfter;
        }
    }

    private static final class BlockchainStub {
        private final HttpServer server;
        private final AtomicLong delayMs = new AtomicLong();
        private final CopyOnWriteArrayList<StubCall> calls = new CopyOnWriteArrayList<>();

        private BlockchainStub(HttpServer server) {
            this.server = server;
        }

        static BlockchainStub start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                BlockchainStub stub = new BlockchainStub(server);
                server.createContext("/anchor", stub::handle);
                server.start();
                return stub;
            } catch (IOException ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/anchor";
        }

        void reset(long responseDelayMs) {
            calls.clear();
            delayMs.set(responseDelayMs);
        }

        List<StubCall> calls() {
            return List.copyOf(calls);
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            Instant begin = Instant.now();
            long started = System.nanoTime();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                Thread.sleep(delayMs.get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] response = "{\"transactionHash\":\"0xc3-after-stub\",\"blockNumber\":3002}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            calls.add(new StubCall(body, nanosToMillis(System.nanoTime() - started), begin, Instant.now()));
        }
    }

    private record StubCall(String body, double durationMs, Instant begin, Instant end) {
    }

    private static final class ForcedFailure extends RuntimeException {
        private ForcedFailure(String message) {
            super(message);
        }
    }
}
