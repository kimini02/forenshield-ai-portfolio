package com.example.demo.performance.c3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.CaseReviewStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
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
import com.example.demo.repository.ReportPublicationSnapshotRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.evidence.CaseDetailAssembler;
import com.example.demo.service.report.ReportPublicationSnapshotService;
import com.example.demo.support.JwtTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Tag;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.maximum-pool-size=5",
        "blockchain.anchor.enabled=true",
        "blockchain.anchor.mode=http",
        "blockchain.anchor.network=c3-local-stub",
        "blockchain.anchor.scheduler-enabled=false",
        "analysis.worker.stale-reaper-enabled=false",
        "hls.packaging.enabled=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
@Tag("c3-before-reproduction")
class ApprovalTransactionBeforeReproductionTest {

    private static final String PASSWORD = "c3-test-password";
    private static final String RESULT_PREFIX = "C3_RESULT";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("forenshield_c3_before")
            .withUsername("forenshield")
            .withPassword("forenshield")
            .withInitScript("db/test/postgresql-domains.sql");

    private static final Path UPLOAD_ROOT = createUploadRoot();
    private static final BlockchainStub BLOCKCHAIN_STUB = BlockchainStub.start();
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @DynamicPropertySource
    static void configureEnvironment(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?ApplicationName=c3-baseline");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("blockchain.anchor.http-url", BLOCKCHAIN_STUB::url);
        registry.add("file.upload-dir", UPLOAD_ROOT::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private CaseProfileRepository caseProfileRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private AnalysisModuleResultRepository analysisModuleResultRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportPublicationSnapshotRepository snapshotRepository;

    @Autowired
    private BlockchainAnchorRepository blockchainAnchorRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @SpyBean
    private ReportPublicationSnapshotService snapshotService;

    @SpyBean
    private CaseDetailAssembler caseDetailAssembler;

    @BeforeEach
    void cleanDatabaseAndStub() {
        reset(snapshotService, caseDetailAssembler);
        notificationRepository.deleteAll();
        custodyLogRepository.deleteAll();
        blockchainAnchorRepository.deleteAll();
        snapshotRepository.deleteAll();
        reportRepository.deleteAll();
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
        reset(snapshotService, caseDetailAssembler);
    }

    @AfterAll
    static void stopStub() {
        BLOCKCHAIN_STUB.stop();
    }

    @Test
    @Order(1)
    void normalApprovalCompletesTheRealApiFlow() throws Exception {
        ApprovalFixture fixture = createFixture("normal");
        BLOCKCHAIN_STUB.reset(0L);

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        printResult("ENV",
                "postgresImage=" + POSTGRES.getDockerImageName(),
                "postgresVersion=" + postgresVersion(),
                "javaVersion=" + System.getProperty("java.version"),
                "hikariMaxPool=" + hikari.getMaximumPoolSize(),
                "blockchainMode=http",
                "pdfRoot=isolated-temp-directory");

        ApprovalMeasurement measurement = invokeApproval(fixture, true);

        CaseProfile profile = caseProfileRepository.findById(fixture.caseProfileId()).orElseThrow();
        Report report = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId())
                .orElseThrow();
        BlockchainAnchor anchor = blockchainAnchorRepository
                .findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                        report.getReportId(), com.example.demo.domain.enums.BlockchainAnchorType.REPORT_HASH)
                .orElseThrow();
        Path pdf = Path.of(report.getStoragePath());

        assertThat(measurement.httpStatus()).isEqualTo(200);
        assertThat(profile.getReviewStatus()).isEqualTo(CaseReviewStatus.REPORT_APPROVED);
        assertThat(report.getPublicationStatus()).isEqualTo(ReportPublicationStatus.ISSUED);
        assertThat(Files.isRegularFile(pdf)).isTrue();
        assertThat(Files.size(pdf)).isPositive();
        assertThat(anchor.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(BLOCKCHAIN_STUB.calls()).hasSize(1);

        printResult("NORMAL",
                "status=200",
                "profile=REPORT_APPROVED",
                "report=ISSUED",
                "pdfBytes=" + Files.size(pdf),
                "anchor=ANCHORED",
                "stubCalls=1");
    }

    @Test
    @Order(2)
    void measuresBlockchainDelayAgainstTransactionDuration() throws Exception {
        for (long delayMs : List.of(0L, 1_000L, 5_000L)) {
            for (int run = 1; run <= 3; run++) {
                ApprovalFixture fixture = createFixture("delay-" + delayMs + "-" + run);
                BLOCKCHAIN_STUB.reset(delayMs);

                ApprovalMeasurement measurement = invokeApproval(fixture, true);

                assertThat(measurement.httpStatus()).isEqualTo(200);
                assertThat(measurement.transactionDurationMs()).isGreaterThanOrEqualTo(0L);
                assertThat(BLOCKCHAIN_STUB.calls()).hasSize(1);
                printResult("DELAY",
                        "delayMs=" + delayMs,
                        "run=" + run,
                        "apiMs=" + format(measurement.apiLatencyMs()),
                        "txBegin=" + measurement.transactionBegin(),
                        "txEnd=" + measurement.transactionEnd(),
                        "txMs=" + measurement.transactionDurationMs(),
                        "maxXactAgeMs=" + format(measurement.maxTransactionAgeMs()),
                        "httpBegin=" + measurement.httpBegin(),
                        "httpEnd=" + measurement.httpEnd(),
                        "httpMs=" + format(measurement.httpDurationMs()),
                        "hikariBefore=" + measurement.hikariBefore(),
                        "hikariMax=" + measurement.hikariMax(),
                        "hikariAfter=" + measurement.hikariAfter(),
                        "stubCalls=" + BLOCKCHAIN_STUB.calls().size());
            }
        }
    }

    @Test
    @Order(3)
    void pdfFileRemainsWhenDatabaseRollsBackAfterFileWrite() throws Exception {
        ApprovalFixture fixture = createFixture("pdf-rollback");
        doThrow(new ForcedFailure("forced failure after PDF file write"))
                .when(snapshotService).createIfAbsent(any(Report.class), anyList());

        ApprovalMeasurement measurement = invokeApproval(fixture, false);
        List<Path> pdfFiles = findPdfFiles(fixture.evidenceId());

        assertThat(measurement.httpStatus()).isGreaterThanOrEqualTo(500);
        assertThat(caseProfileRepository.findById(fixture.caseProfileId()).orElseThrow().getReviewStatus())
                .isEqualTo(CaseReviewStatus.REVIEW_ASSIGNED);
        assertThat(reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()))
                .isEmpty();
        assertThat(snapshotRepository.count()).isZero();
        assertThat(blockchainAnchorRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
        assertThat(custodyLogRepository.count()).isZero();
        assertThat(BLOCKCHAIN_STUB.calls()).isEmpty();
        assertThat(pdfFiles).hasSize(1);
        assertThat(Files.size(pdfFiles.get(0))).isPositive();

        printResult("PDF_ROLLBACK",
                "httpStatus=" + measurement.httpStatus(),
                "profile=REVIEW_ASSIGNED",
                "reportCount=" + reportRepository.count(),
                "snapshotCount=" + snapshotRepository.count(),
                "anchorCount=" + blockchainAnchorRepository.count(),
                "notificationCount=" + notificationRepository.count(),
                "custodyCount=" + custodyLogRepository.count(),
                "stubCalls=" + BLOCKCHAIN_STUB.calls().size(),
                "pdfExists=true",
                "pdfBytes=" + Files.size(pdfFiles.get(0)),
                "pdfSha256=" + sha256(pdfFiles.get(0)));
    }

    @Test
    @Order(4)
    void successfulBlockchainCallRemainsWhenLaterDatabaseTransactionRollsBack() throws Exception {
        ApprovalFixture fixture = createFixture("anchor-rollback");
        doThrow(new ForcedFailure("forced failure after blockchain HTTP success"))
                .when(caseDetailAssembler).assemble(
                        any(User.class),
                        anyString(),
                        anyList(),
                        anyList(),
                        any(CaseProfile.class),
                        any(User.class),
                        anyMap());

        BLOCKCHAIN_STUB.reset(0L);
        ApprovalMeasurement measurement = invokeApproval(fixture, false);
        List<Path> pdfFiles = findPdfFiles(fixture.evidenceId());
        StubCall stubCall = BLOCKCHAIN_STUB.calls().get(0);
        JsonNode payload = objectMapper.readTree(stubCall.body());

        assertThat(measurement.httpStatus()).isGreaterThanOrEqualTo(500);
        assertThat(caseProfileRepository.findById(fixture.caseProfileId()).orElseThrow().getReviewStatus())
                .isEqualTo(CaseReviewStatus.REVIEW_ASSIGNED);
        assertThat(reportRepository.findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()))
                .isEmpty();
        assertThat(snapshotRepository.count()).isZero();
        assertThat(blockchainAnchorRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
        assertThat(custodyLogRepository.count()).isZero();
        assertThat(BLOCKCHAIN_STUB.calls()).hasSize(1);
        assertThat(pdfFiles).hasSize(1);
        assertThat(payload.path("anchorType").asText()).isEqualTo("REPORT_HASH");
        assertThat(payload.path("reportId").isIntegralNumber()).isTrue();

        printResult("ANCHOR_ROLLBACK",
                "httpStatus=" + measurement.httpStatus(),
                "profile=REVIEW_ASSIGNED",
                "reportCount=" + reportRepository.count(),
                "snapshotCount=" + snapshotRepository.count(),
                "anchorCount=" + blockchainAnchorRepository.count(),
                "notificationCount=" + notificationRepository.count(),
                "custodyCount=" + custodyLogRepository.count(),
                "stubCalls=" + BLOCKCHAIN_STUB.calls().size(),
                "stubAnchorType=" + payload.path("anchorType").asText(),
                "stubReportId=" + payload.path("reportId").asLong(),
                "stubSubjectHash=" + payload.path("subjectHash").asText(),
                "pdfExists=true",
                "pdfBytes=" + Files.size(pdfFiles.get(0)),
                "pdfSha256=" + sha256(pdfFiles.get(0)));
    }

    private ApprovalFixture createFixture(String label) throws Exception {
        int sequence = FIXTURE_SEQUENCE.incrementAndGet();
        String suffix = label.replaceAll("[^a-zA-Z0-9]", "-") + "-" + sequence;
        String investigatorLogin = "c3-inv-" + suffix;
        String reviewerLogin = "c3-rev-" + suffix;
        String caseKey = "C3-CASE-" + suffix;

        User investigator = userRepository.saveAndFlush(User.builder()
                .loginId(investigatorLogin)
                .email(investigatorLogin + "@test.local")
                .password(passwordEncoder.encode(PASSWORD))
                .name("C3 investigator")
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
                .name("C3 reviewer")
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
                .fileName("c3-" + suffix + ".mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue(String.format("%064x", sequence))
                .originalStoragePath("c3-fixture/" + suffix + ".mp4")
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
        result.setSummary("C3 isolated approval fixture");
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
        module.setModelVersion("before-v1");
        module.setDetailsJson("{}");
        module.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.saveAndFlush(module);

        CaseProfile profile = new CaseProfile(investigator.getUserId(), caseKey, evidence.getEvidenceId());
        profile.assignReviewer(reviewer.getUserId());
        profile = caseProfileRepository.saveAndFlush(profile);

        String reviewerToken = JwtTestSupport.loginAndGetToken(mockMvc, reviewerLogin, PASSWORD);
        return new ApprovalFixture(
                caseKey,
                evidence.getEvidenceId(),
                result.getAnalysisResultId(),
                profile.getCaseProfileId(),
                reviewerToken);
    }

    private ApprovalMeasurement invokeApproval(ApprovalFixture fixture, boolean expectedSuccess) throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        int hikariBefore = hikari.getHikariPoolMXBean().getActiveConnections();
        ActivitySampler sampler = new ActivitySampler(hikari);
        TransactionLogCapture transactionLogs = TransactionLogCapture.start();
        sampler.start();

        long started = System.nanoTime();
        int status;
        Throwable requestFailure = null;
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/cases/review-decision")
                            .queryParam("caseKey", fixture.caseKey())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.reviewerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"APPROVED\",\"memo\":\"C3 before reproduction\"}"))
                    .andReturn();
            status = result.getResponse().getStatus();
        } catch (Throwable failure) {
            requestFailure = failure;
            status = 500;
        } finally {
            sampler.stopAndJoin();
            transactionLogs.stop();
        }
        double apiLatencyMs = nanosToMillis(System.nanoTime() - started);
        int hikariAfter = hikari.getHikariPoolMXBean().getActiveConnections();

        if (expectedSuccess) {
            assertThat(requestFailure).isNull();
            assertThat(status).isEqualTo(200);
        } else {
            assertThat(status).isGreaterThanOrEqualTo(500);
        }
        assertThat(sampler.failure()).isNull();

        TransactionWindow window = transactionLogs.findRecordDecisionWindow();
        List<StubCall> stubCalls = BLOCKCHAIN_STUB.calls();
        double httpDurationMs = stubCalls.stream().mapToDouble(StubCall::durationMs).sum();
        String httpBegin = stubCalls.isEmpty() ? "not-called" : stubCalls.get(0).begin().toString();
        String httpEnd = stubCalls.isEmpty() ? "not-called" : stubCalls.get(stubCalls.size() - 1).end().toString();
        return new ApprovalMeasurement(
                status,
                apiLatencyMs,
                window.begin().toString(),
                window.end().toString(),
                window.durationMs(),
                sampler.maxTransactionAgeMs(),
                httpBegin,
                httpEnd,
                httpDurationMs,
                hikariBefore,
                sampler.maxHikariActive(),
                hikariAfter);
    }

    private List<Path> findPdfFiles(Long evidenceId) throws IOException {
        Path evidenceDirectory = UPLOAD_ROOT.resolve("reports").resolve("evidence").resolve(String.valueOf(evidenceId));
        if (!Files.isDirectory(evidenceDirectory)) {
            return List.of();
        }
        try (var paths = Files.list(evidenceDirectory)) {
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

    private static Path createUploadRoot() {
        try {
            return Files.createTempDirectory("forenshield-c3-before-");
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

    private record ApprovalFixture(
            String caseKey,
            Long evidenceId,
            Long analysisResultId,
            Long caseProfileId,
            String reviewerToken
    ) {
    }

    private record ApprovalMeasurement(
            int httpStatus,
            double apiLatencyMs,
            String transactionBegin,
            String transactionEnd,
            long transactionDurationMs,
            double maxTransactionAgeMs,
            String httpBegin,
            String httpEnd,
            double httpDurationMs,
            int hikariBefore,
            int hikariMax,
            int hikariAfter
    ) {
    }

    private record TransactionWindow(Instant begin, Instant end, long durationMs) {
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

        TransactionWindow findRecordDecisionWindow() {
            List<ILoggingEvent> events = new ArrayList<>(appender.list);
            int beginIndex = -1;
            for (int index = 0; index < events.size(); index++) {
                if (events.get(index).getFormattedMessage().contains(
                        "Creating new transaction with name [com.example.demo.service.evidence.CaseReviewService.recordDecision]")) {
                    beginIndex = index;
                    break;
                }
            }
            assertThat(beginIndex).as("recordDecision transaction begin log").isGreaterThanOrEqualTo(0);

            ILoggingEvent begin = events.get(beginIndex);
            ILoggingEvent end = null;
            for (int index = beginIndex + 1; index < events.size(); index++) {
                String message = events.get(index).getFormattedMessage();
                if (message.contains("Initiating transaction commit")
                        || message.contains("Initiating transaction rollback")) {
                    end = events.get(index);
                    break;
                }
            }
            assertThat(end).as("recordDecision transaction end log").isNotNull();
            Instant beginInstant = Instant.ofEpochMilli(begin.getTimeStamp());
            Instant endInstant = Instant.ofEpochMilli(end.getTimeStamp());
            return new TransactionWindow(beginInstant, endInstant, endInstant.toEpochMilli() - beginInstant.toEpochMilli());
        }
    }

    private final class ActivitySampler {

        private final HikariDataSource hikari;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicLong maxTransactionAgeMicros = new AtomicLong();
        private final AtomicInteger maxHikariActive = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private Thread thread;

        private ActivitySampler(HikariDataSource hikari) {
            this.hikari = hikari;
        }

        void start() {
            running.set(true);
            thread = new Thread(this::sample, "c3-pg-activity-sampler");
            thread.setDaemon(true);
            thread.start();
        }

        private void sample() {
            String samplerUrl = POSTGRES.getJdbcUrl() + "?ApplicationName=c3-sampler";
            String sql = """
                    SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (clock_timestamp() - xact_start)) * 1000000), 0)
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND application_name <> 'c3-sampler'
                      AND xact_start IS NOT NULL
                    """;
            try (Connection connection = DriverManager.getConnection(
                    samplerUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = connection.createStatement()) {
                while (running.get()) {
                    try (ResultSet resultSet = statement.executeQuery(sql)) {
                        if (resultSet.next()) {
                            maxTransactionAgeMicros.accumulateAndGet(resultSet.getLong(1), Math::max);
                        }
                    }
                    maxHikariActive.accumulateAndGet(
                            hikari.getHikariPoolMXBean().getActiveConnections(), Math::max);
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

        int maxHikariActive() {
            return maxHikariActive.get();
        }

        Throwable failure() {
            return failure.get();
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

            byte[] response = "{\"transactionHash\":\"0xc3-local-stub\",\"blockNumber\":3001}"
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
