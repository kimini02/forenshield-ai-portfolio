package com.example.demo.performance.c4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.ReportIssueTask;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CaseReviewStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.ReportIssueTaskStatus;
import com.example.demo.domain.enums.ReportPublicationStatus;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportIssueTaskInsertRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportPublicationSnapshotRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.report.ReportIssueTaskProcessor;
import com.example.demo.service.report.ReportIssueTaskStateService;
import com.example.demo.support.JwtTestSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.maximum-pool-size=20",
        "report.issue.worker.enabled=false",
        "report.issue.worker.batch-size=20",
        "blockchain.anchor.enabled=true",
        "blockchain.anchor.mode=http",
        "blockchain.anchor.scheduler-enabled=false",
        "analysis.worker.stale-reaper-enabled=false",
        "hls.packaging.enabled=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
class ConcurrentApprovalAfterVerificationTest {

    private static final String PASSWORD = "c4-after-isolated-password";
    private static final String RESULT_PREFIX = "C4_AFTER_RESULT";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Path UPLOAD_ROOT = createUploadRoot();
    private static final BlockchainStub BLOCKCHAIN_STUB = BlockchainStub.start();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("forenshield_c4_after")
            .withUsername("forenshield")
            .withPassword("forenshield")
            .withInitScript("db/test/postgresql-domains.sql");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?ApplicationName=c4-after");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("file.upload-dir", UPLOAD_ROOT::toString);
        registry.add("blockchain.anchor.http-url", BLOCKCHAIN_STUB::url);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private EvidenceRepository evidenceRepository;
    @Autowired private AnalysisRequestRepository analysisRequestRepository;
    @Autowired private AnalysisResultRepository analysisResultRepository;
    @Autowired private CaseProfileRepository caseProfileRepository;
    @Autowired private ReportIssueTaskRepository taskRepository;
    @Autowired private ReportIssueTaskInsertRepository taskInsertRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private ReportPublicationSnapshotRepository snapshotRepository;
    @Autowired private BlockchainAnchorRepository anchorRepository;
    @Autowired private ReportIssueTaskStateService taskStateService;
    @Autowired private ReportIssueTaskProcessor taskProcessor;

    @BeforeEach
    void clearDownstreamState() {
        anchorRepository.deleteAll();
        snapshotRepository.deleteAll();
        reportRepository.deleteAll();
        taskRepository.deleteAll();
        BLOCKCHAIN_STUB.reset();
    }

    @AfterAll
    static void stopStub() {
        BLOCKCHAIN_STUB.stop();
    }

    @Test
    @Order(1)
    void databaseConstraintRejectsDuplicateAndAllowsDifferentAnalysisResults() throws Exception {
        Fixture first = createFixture("constraint-first", 1);
        Fixture second = createFixture("constraint-second", 1);
        insertRawTask(first, first.firstResultId());

        SQLException duplicate = null;
        try {
            insertRawTask(first, first.firstResultId());
        } catch (SQLException failure) {
            duplicate = failure;
        }
        insertRawTask(second, second.firstResultId());

        assertThat((Throwable) duplicate).isNotNull();
        assertThat(duplicate.getSQLState()).isEqualTo("23505");
        assertThat(countTasks(first.firstResultId())).isEqualTo(1);
        assertThat(countTasks(second.firstResultId())).isEqualTo(1);
        printResult("CONSTRAINT", "duplicateSqlState=" + duplicate.getSQLState(),
                "sameResultRows=1", "differentResultRows=1", "constraint=" + constraintName());
    }

    @Test
    @Order(2)
    void atomicInsertAllowsBothTransactionsToCommitAndKeepsOneTask() throws Exception {
        Fixture fixture = createFixture("atomic-race", 1);
        CyclicBarrier simultaneousInsert = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<AtomicResult>> futures = new ArrayList<>();
            for (int worker = 1; worker <= 2; worker++) {
                int workerId = worker;
                futures.add(executor.submit(() -> atomicInsert(fixture, workerId, simultaneousInsert)));
            }
            List<AtomicResult> results = futures.stream().map(this::await).toList();
            List<Integer> affected = results.stream().map(AtomicResult::affectedRows).sorted().toList();
            long taskRows = countTasks(fixture.firstResultId());

            assertThat(results).allMatch(AtomicResult::committed);
            assertThat(affected).containsExactly(0, 1);
            assertThat(taskRows).isEqualTo(1);
            printResult("ATOMIC_RACE", "concurrent=2", "txCommitted=2", "txFailed=0",
                    "affectedRows=" + affected, "taskRows=" + taskRows);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(3)
    void actualApprovalApiConcurrencyKeepsOneTask() throws Exception {
        printResult("ENV", "postgresImage=" + POSTGRES.getDockerImageName(),
                "postgresVersion=" + postgresVersion(), "javaVersion=" + System.getProperty("java.version"),
                "isolation=READ_COMMITTED", "schedulerEnabled=false");
        for (int concurrent : List.of(2, 5, 10)) {
            for (int run = 1; run <= 3; run++) {
                Fixture fixture = createFixture("http-" + concurrent + "-" + run, 1);
                HttpRun result = invokeConcurrentApprovals(fixture, concurrent);
                long taskRows = countTasks(fixture.firstResultId());
                printResult("HTTP_APPROVAL", "concurrent=" + concurrent, "run=" + run,
                        "http200=" + result.success(), "httpOther=" + result.failure(),
                        "taskRows=" + taskRows, "maxLatencyMs=" + format(result.maxLatencyMs()));
                assertThat(result.success()).isEqualTo(concurrent);
                assertThat(result.failure()).isZero();
                assertThat(taskRows).isEqualTo(1);
            }
        }
        assertThat(duplicateGroupCount()).isZero();
        printResult("DUPLICATE_QUERY", "rows=0");
    }

    @Test
    @Order(4)
    void downstreamProcessesExactlyOneTaskAndProducesOneOfEachSideEffect() throws Exception {
        Fixture fixture = createFixture("downstream", 1);
        HttpRun approval = invokeConcurrentApprovals(fixture, 2);
        assertThat(approval.success()).isEqualTo(2);
        assertThat(countTasks(fixture.firstResultId())).isEqualTo(1);

        List<Long> claimed = taskStateService.claimBatch();
        assertThat(claimed).hasSize(1);
        taskProcessor.process(claimed.get(0));

        ReportIssueTask task = taskRepository.findById(claimed.get(0)).orElseThrow();
        List<Report> reports = reportsFor(fixture.firstResultId());
        List<BlockchainAnchor> anchors = anchorsFor(reports);
        long pdfFiles = countPdfFiles(fixture.firstEvidenceId());
        printResult("DOWNSTREAM", "taskRows=1", "claimed=" + claimed.size(),
                "completedTasks=" + (task.getStatus() == ReportIssueTaskStatus.COMPLETED ? 1 : 0),
                "reportRows=" + reports.size(), "pdfFiles=" + pdfFiles,
                "blockchainHttpCalls=" + BLOCKCHAIN_STUB.calls(), "anchorRows=" + anchors.size());

        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
        assertThat(reports).hasSize(1);
        assertThat(pdfFiles).isEqualTo(1);
        assertThat(BLOCKCHAIN_STUB.calls()).isEqualTo(1);
        assertThat(anchors).hasSize(1);
    }

    @Test
    @Order(5)
    void concurrentApprovalCreatesOneTaskForEachOfMultipleAnalysisResults() throws Exception {
        Fixture fixture = createFixture("multi-result", 3);
        HttpRun result = invokeConcurrentApprovals(fixture, 2);
        List<Long> rowsByResult = fixture.analysisResultIds().stream().map(this::countTasks).toList();
        printResult("MULTI_RESULT", "concurrent=2", "http200=" + result.success(),
                "httpOther=" + result.failure(), "resultCount=3", "rowsByResult=" + rowsByResult,
                "totalTasks=" + taskRowsForCase(fixture.caseProfileId()));

        assertThat(result.success()).isEqualTo(2);
        assertThat(result.failure()).isZero();
        assertThat(rowsByResult).containsExactly(1L, 1L, 1L);
        assertThat(taskRowsForCase(fixture.caseProfileId())).isEqualTo(3);
    }

    @Test
    @Order(6)
    void sequentialApprovalAndEveryExistingTaskStateRemainIdempotent() throws Exception {
        Fixture sequential = createFixture("sequential", 1);
        assertThat(invokeConcurrentApprovals(sequential, 1).success()).isEqualTo(1);
        Long originalTaskId = onlyTask(sequential.firstResultId()).getReportIssueTaskId();
        assertThat(invokeConcurrentApprovals(sequential, 1).success()).isEqualTo(1);
        assertThat(onlyTask(sequential.firstResultId()).getReportIssueTaskId()).isEqualTo(originalTaskId);

        for (ReportIssueTaskStatus status : ReportIssueTaskStatus.values()) {
            Fixture fixture = createFixture("state-" + status.name().toLowerCase(), 1);
            ReportIssueTask task = taskWithStatus(fixture, status);
            taskRepository.saveAndFlush(task);
            Long taskId = task.getReportIssueTaskId();

            HttpRun response = invokeConcurrentApprovals(fixture, 1);
            ReportIssueTask unchanged = onlyTask(fixture.firstResultId());
            assertThat(response.success()).isEqualTo(1);
            assertThat(unchanged.getReportIssueTaskId()).isEqualTo(taskId);
            assertThat(unchanged.getStatus()).isEqualTo(status);
            printResult("EXISTING_STATE", "status=" + status, "http200=1", "inserted=0",
                    "taskRows=1", "sameTaskId=true", "stateUnchanged=true");
        }
    }

    @Test
    @Order(7)
    void issuedReportWithoutTaskKeepsCompatibilitySkip() throws Exception {
        Fixture fixture = createFixture("issued-report", 1);
        reportRepository.saveAndFlush(issuedReport(fixture));

        HttpRun response = invokeConcurrentApprovals(fixture, 1);
        assertThat(response.success()).isEqualTo(1);
        assertThat(countTasks(fixture.firstResultId())).isZero();
        printResult("ISSUED_REPORT", "http200=1", "taskRows=0", "compatibilitySkip=true");
    }

    @Test
    @Order(8)
    void approvalAndAtomicTaskInsertRollBackTogetherOnLaterFailure() throws Exception {
        Fixture fixture = createFixture("rollback", 1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            CaseProfile profile = caseProfileRepository.findById(fixture.caseProfileId()).orElseThrow();
            profile.approveReview();
            caseProfileRepository.save(profile);
            int inserted = taskInsertRepository.insertPendingIfAbsent(
                    fixture.caseProfileId(), fixture.firstEvidenceId(), fixture.firstResultId(),
                    fixture.reviewerId(), LocalDateTime.now());
            assertThat(inserted).isEqualTo(1);
            throw new IllegalStateException("forced failure after atomic task insert");
        })).isInstanceOf(IllegalStateException.class);

        CaseProfile rolledBack = caseProfileRepository.findById(fixture.caseProfileId()).orElseThrow();
        assertThat(rolledBack.getReviewStatus()).isEqualTo(CaseReviewStatus.REVIEW_ASSIGNED);
        assertThat(countTasks(fixture.firstResultId())).isZero();
        printResult("ROLLBACK", "profile=REVIEW_ASSIGNED", "taskRows=0", "sameTransaction=true");
    }

    private AtomicResult atomicInsert(Fixture fixture, int workerId, CyclicBarrier barrier) {
        try {
            Integer affected = new TransactionTemplate(transactionManager).execute(status -> {
                awaitBarrier(barrier);
                return taskInsertRepository.insertPendingIfAbsent(
                        fixture.caseProfileId(), fixture.firstEvidenceId(), fixture.firstResultId(),
                        fixture.reviewerId(), LocalDateTime.now());
            });
            return new AtomicResult(workerId, true, affected == null ? -1 : affected, null);
        } catch (Exception failure) {
            return new AtomicResult(workerId, false, -1, failure.getClass().getSimpleName());
        }
    }

    private HttpRun invokeConcurrentApprovals(Fixture fixture, int concurrent) throws Exception {
        CountDownLatch ready = new CountDownLatch(concurrent);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concurrent);
        try {
            List<Future<HttpResult>> futures = new ArrayList<>();
            for (int index = 0; index < concurrent; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        return new HttpResult(0, 0);
                    }
                    long started = System.nanoTime();
                    MvcResult response = mockMvc.perform(post("/api/v1/cases/review-decision")
                                    .queryParam("caseKey", fixture.caseKey())
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.reviewerToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"decision\":\"APPROVED\",\"memo\":\"C4 after\"}"))
                            .andReturn();
                    return new HttpResult(response.getResponse().getStatus(), millisSince(started));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<HttpResult> results = futures.stream().map(this::await).toList();
            int success = (int) results.stream().filter(result -> result.status() == 200).count();
            return new HttpRun(success, concurrent - success,
                    results.stream().mapToDouble(HttpResult::latencyMs).max().orElse(0));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Fixture createFixture(String label, int resultCount) throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        String suffix = label + "-" + sequence;
        String investigatorLogin = "c4-after-inv-" + suffix;
        String reviewerLogin = "c4-after-rev-" + suffix;
        String caseKey = "C4-AFTER-" + suffix;
        User investigator = userRepository.saveAndFlush(user(investigatorLogin, UserRole.ROLE_INVESTIGATOR));
        User reviewer = userRepository.saveAndFlush(user(reviewerLogin, UserRole.ROLE_REVIEWER));

        List<Long> evidenceIds = new ArrayList<>();
        List<Long> resultIds = new ArrayList<>();
        for (int index = 0; index < resultCount; index++) {
            Evidence evidence = evidenceRepository.saveAndFlush(Evidence.builder()
                    .uploaderId(investigator.getUserId()).caseName(caseKey).caseNumber(caseKey)
                    .fileName("c4-after-" + suffix + "-" + index + ".mp4")
                    .fileType(FileType.VIDEO).mimeType("video/mp4").fileSize(12L)
                    .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                    .originalHashValue(String.format("%064x", sequence * 10L + index))
                    .originalStoragePath("c4-after-fixture/" + suffix + "-" + index + ".mp4")
                    .uploadedAt(LocalDateTime.now().plusNanos(index)).build());
            AnalysisRequest request = new AnalysisRequest();
            request.setEvidenceId(evidence.getEvidenceId());
            request.setRequestedBy(investigator.getUserId());
            request.setStatus(AnalysisStatus.COMPLETED);
            request.setProgressPercent(100);
            request.setRequestedAt(LocalDateTime.now());
            request.setStartedAt(LocalDateTime.now());
            request.setCompletedAt(LocalDateTime.now());
            request = analysisRequestRepository.saveAndFlush(request);
            AnalysisResult result = new AnalysisResult();
            result.setAnalysisRequestId(request.getAnalysisRequestId());
            result.setRiskScore(64.0);
            result.setConfidenceScore(0.88);
            result.setRiskLevel(RiskLevel.MEDIUM);
            result.setSummary("C4 after fixture");
            result.setAnalyzedAt(LocalDateTime.now());
            result = analysisResultRepository.saveAndFlush(result);
            evidenceIds.add(evidence.getEvidenceId());
            resultIds.add(result.getAnalysisResultId());
        }

        CaseProfile profile = new CaseProfile(investigator.getUserId(), caseKey, evidenceIds.get(0));
        profile.assignReviewer(reviewer.getUserId());
        profile = caseProfileRepository.saveAndFlush(profile);
        String token = JwtTestSupport.loginAndGetToken(mockMvc, reviewerLogin, PASSWORD);
        return new Fixture(caseKey, evidenceIds, resultIds, profile.getCaseProfileId(), reviewer.getUserId(), token);
    }

    private User user(String loginId, UserRole role) {
        return User.builder().loginId(loginId).email(loginId + "@test.local")
                .password(passwordEncoder.encode(PASSWORD)).name("C4 after user")
                .organizationType(OrgType.POLICE).department("C4 isolated")
                .role(role).status(UserStatus.APPROVED).darkMode(false).build();
    }

    private ReportIssueTask taskWithStatus(Fixture fixture, ReportIssueTaskStatus status) {
        ReportIssueTask task = ReportIssueTask.pending(
                fixture.caseProfileId(), fixture.firstEvidenceId(), fixture.firstResultId(), fixture.reviewerId());
        LocalDateTime now = LocalDateTime.now();
        if (status != ReportIssueTaskStatus.PENDING) {
            task.claim(now);
        }
        if (status == ReportIssueTaskStatus.COMPLETED) {
            task.complete(now);
        } else if (status == ReportIssueTaskStatus.FAILED) {
            task.fail("terminal test failure", now);
        }
        return task;
    }

    private Report issuedReport(Fixture fixture) {
        Report report = new Report();
        report.setAnalysisResultId(fixture.firstResultId());
        report.setEvidenceId(fixture.firstEvidenceId());
        report.setCreatedBy(fixture.reviewerId());
        report.setStoragePath("c4-existing/issued.pdf");
        report.setReportFileName("issued.pdf");
        report.setPublicationStatus(ReportPublicationStatus.ISSUED);
        report.setCreatedAt(LocalDateTime.now());
        report.markIssued(fixture.reviewerId(), LocalDateTime.now());
        return report;
    }

    private void insertRawTask(Fixture fixture, Long analysisResultId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO report_issue_tasks (
                         case_profile_id, evidence_id, analysis_result_id, requested_by,
                         status, attempt_count, created_at, updated_at
                     ) VALUES (?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                     """)) {
            statement.setLong(1, fixture.caseProfileId());
            statement.setLong(2, fixture.firstEvidenceId());
            statement.setLong(3, analysisResultId);
            statement.setLong(4, fixture.reviewerId());
            statement.executeUpdate();
        }
    }

    private String constraintName() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT conname
                     FROM pg_constraint
                     WHERE conrelid = 'report_issue_tasks'::regclass
                       AND contype = 'u'
                     """);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getString(1);
        }
    }

    private long duplicateGroupCount() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM (
                         SELECT analysis_result_id
                         FROM report_issue_tasks
                         GROUP BY analysis_result_id
                         HAVING COUNT(*) > 1
                     ) duplicates
                     """);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private long countTasks(Long analysisResultId) {
        return taskRepository.findAll().stream()
                .filter(task -> analysisResultId.equals(task.getAnalysisResultId())).count();
    }

    private long taskRowsForCase(Long caseProfileId) {
        return taskRepository.findAll().stream()
                .filter(task -> caseProfileId.equals(task.getCaseProfileId())).count();
    }

    private ReportIssueTask onlyTask(Long analysisResultId) {
        return taskRepository.findAll().stream()
                .filter(task -> analysisResultId.equals(task.getAnalysisResultId())).findFirst().orElseThrow();
    }

    private List<Report> reportsFor(Long analysisResultId) {
        return reportRepository.findAll().stream()
                .filter(report -> analysisResultId.equals(report.getAnalysisResultId())).toList();
    }

    private List<BlockchainAnchor> anchorsFor(List<Report> reports) {
        return anchorRepository.findAll().stream()
                .filter(anchor -> reports.stream().anyMatch(report -> report.getReportId().equals(anchor.getReportId())))
                .toList();
    }

    private long countPdfFiles(Long evidenceId) {
        Path directory = UPLOAD_ROOT.resolve("reports/evidence/" + evidenceId);
        if (!Files.exists(directory)) {
            return 0;
        }
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".pdf")).count();
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private String postgresVersion() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SHOW server_version");
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static Path createUploadRoot() {
        try {
            return Files.createTempDirectory("forenshield-c4-after-");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private <T> T await(Future<T> future) {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static double millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000.0;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static void printResult(String type, String... values) {
        System.out.println(RESULT_PREFIX + "\t" + type + "\t" + String.join("\t", values));
    }

    private record Fixture(
            String caseKey,
            List<Long> evidenceIds,
            List<Long> analysisResultIds,
            Long caseProfileId,
            Long reviewerId,
            String reviewerToken
    ) {
        Long firstEvidenceId() {
            return evidenceIds.get(0);
        }

        Long firstResultId() {
            return analysisResultIds.get(0);
        }
    }

    private record AtomicResult(int workerId, boolean committed, int affectedRows, String error) {
    }

    private record HttpResult(int status, double latencyMs) {
    }

    private record HttpRun(int success, int failure, double maxLatencyMs) {
    }

    private static final class BlockchainStub {
        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();

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
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/anchor";
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().readAllBytes();
            int call = calls.incrementAndGet();
            byte[] response = ("{\"transactionHash\":\"0xc4-after-" + call
                    + "\",\"blockNumber\":" + (5000 + call) + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }
}
