package com.example.demo.performance.c4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.maximum-pool-size=20",
        "report.issue.worker.enabled=false",
        "report.issue.worker.batch-size=10",
        "blockchain.anchor.enabled=true",
        "blockchain.anchor.mode=http",
        "blockchain.anchor.scheduler-enabled=false",
        "analysis.worker.stale-reaper-enabled=false",
        "hls.packaging.enabled=false"
})
@AutoConfigureMockMvc
@Tag("c4-before-reproduction")
@TestMethodOrder(OrderAnnotation.class)
class ConcurrentApprovalBeforeReproductionTest {

    private static final String PASSWORD = "c4-isolated-test-password";
    private static final String RESULT_PREFIX = "C4_BEFORE_RESULT";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Path UPLOAD_ROOT = createUploadRoot();
    private static final BlockchainStub BLOCKCHAIN_STUB = BlockchainStub.start();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("forenshield_c4_before")
            .withUsername("forenshield")
            .withPassword("forenshield")
            .withInitScript("db/test/postgresql-domains.sql");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?ApplicationName=c4-before");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("file.upload-dir", UPLOAD_ROOT::toString);
        registry.add("blockchain.anchor.http-url", BLOCKCHAIN_STUB::url);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSource dataSource;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private EvidenceRepository evidenceRepository;
    @Autowired private AnalysisRequestRepository analysisRequestRepository;
    @Autowired private AnalysisResultRepository analysisResultRepository;
    @Autowired private CaseProfileRepository caseProfileRepository;
    @Autowired private ReportIssueTaskRepository taskRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private BlockchainAnchorRepository anchorRepository;
    @Autowired private ReportIssueTaskStateService taskStateService;
    @Autowired private ReportIssueTaskProcessor taskProcessor;

    @AfterAll
    static void stopStub() {
        BLOCKCHAIN_STUB.stop();
    }

    @Test
    @Order(1)
    void schemaLevelFindThenInsertRaceAllowsDuplicateTasks() throws Exception {
        Fixture fixture = createFixture("schema-race");
        CyclicBarrier bothObservedMissing = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<TxResult>> futures = new ArrayList<>();
            for (int worker = 1; worker <= 2; worker++) {
                int workerId = worker;
                futures.add(executor.submit(() -> insertAfterSharedMissingCheck(fixture, workerId, bothObservedMissing)));
            }

            List<TxResult> results = futures.stream().map(this::await).toList();
            long taskRows = countTasks(fixture.analysisResultId());
            printResult("SCHEMA_RACE", "concurrent=2", "txCommitted=" + committed(results),
                    "txFailed=" + failed(results), "taskRows=" + taskRows,
                    "analysisResultId=" + fixture.analysisResultId());
            printTaskRows("SCHEMA_RACE_ROWS", fixture.analysisResultId());

            assertThat(results).allMatch(TxResult::committed);
            assertThat(taskRows).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(2)
    void actualApprovalApiConcurrencyIsMeasuredAgainstFinalPostgresRows() throws Exception {
        printResult("ENV", "postgresImage=" + POSTGRES.getDockerImageName(),
                "postgresVersion=" + postgresVersion(),
                "javaVersion=" + System.getProperty("java.version"),
                "isolation=READ_COMMITTED", "schedulerEnabled=false");

        for (int concurrent : List.of(2, 5, 10)) {
            for (int run = 1; run <= 3; run++) {
                Fixture fixture = createFixture("http-" + concurrent + "-" + run);
                HttpRun result = invokeConcurrentApprovals(fixture, concurrent);
                long taskRows = countTasks(fixture.analysisResultId());
                printResult("HTTP_APPROVAL", "concurrent=" + concurrent, "run=" + run,
                        "http200=" + result.success(), "httpOther=" + result.failure(),
                        "taskRows=" + taskRows, "maxLatencyMs=" + format(result.maxLatencyMs()),
                        "analysisResultId=" + fixture.analysisResultId());
                if (concurrent == 2 && run == 1) {
                    printTaskRows("HTTP_APPROVAL_ROWS", fixture.analysisResultId());
                }

                assertThat(result.success() + result.failure()).isEqualTo(concurrent);
                assertThat(taskRows).isGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    @Order(3)
    void duplicateTasksAreProcessedConcurrentlyAndEverySideEffectIsCounted() throws Exception {
        taskRepository.deleteAll();
        anchorRepository.deleteAll();
        reportRepository.deleteAll();
        BLOCKCHAIN_STUB.reset();
        Fixture fixture = createFixture("worker-impact");
        HttpRun approval = invokeConcurrentApprovals(fixture, 2);
        assertThat(approval.success()).isEqualTo(2);
        assertThat(countTasks(fixture.analysisResultId())).isEqualTo(2);

        List<Long> claimed = taskStateService.claimBatch();
        assertThat(claimed).hasSize(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Void>> workers = claimed.stream().map(taskId -> executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                taskProcessor.process(taskId);
                return (Void) null;
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            workers.forEach(this::await);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        List<Report> reports = reportRepository.findAll().stream()
                .filter(report -> fixture.analysisResultId().equals(report.getAnalysisResultId()))
                .toList();
        List<BlockchainAnchor> anchors = anchorRepository.findAll().stream()
                .filter(anchor -> reports.stream().anyMatch(report -> report.getReportId().equals(anchor.getReportId())))
                .toList();
        long pdfFiles = countPdfFiles();
        printResult("DUPLICATE_WORKER", "taskRows=2", "claimed=" + claimed.size(),
                "reportRows=" + reports.size(), "pdfFiles=" + pdfFiles,
                "blockchainHttpCalls=" + BLOCKCHAIN_STUB.calls(), "anchorRows=" + anchors.size());
        printTaskRows("DUPLICATE_WORKER_ROWS", fixture.analysisResultId());

        assertThat(claimed).hasSize(2);
    }

    private TxResult insertAfterSharedMissingCheck(
            Fixture fixture,
            int workerId,
            CyclicBarrier bothObservedMissing
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            boolean exists;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT EXISTS (SELECT 1 FROM report_issue_tasks WHERE analysis_result_id = ?)")
            ) {
                statement.setLong(1, fixture.analysisResultId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    exists = resultSet.getBoolean(1);
                }
            }
            if (exists) {
                connection.rollback();
                return new TxResult(workerId, false, "existing task observed");
            }

            bothObservedMissing.await(10, TimeUnit.SECONDS);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO report_issue_tasks (
                        case_profile_id, evidence_id, analysis_result_id, requested_by,
                        status, attempt_count, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                statement.setLong(1, fixture.caseProfileId());
                statement.setLong(2, fixture.evidenceId());
                statement.setLong(3, fixture.analysisResultId());
                statement.setLong(4, fixture.reviewerId());
                statement.executeUpdate();
            }
            connection.commit();
            return new TxResult(workerId, true, null);
        } catch (Exception failure) {
            return new TxResult(workerId, false, failure.getClass().getSimpleName() + ":" + failure.getMessage());
        }
    }

    private HttpRun invokeConcurrentApprovals(Fixture fixture, int concurrent) throws Exception {
        CountDownLatch ready = new CountDownLatch(concurrent);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concurrent);
        try {
            List<Future<HttpResult>> futures = new ArrayList<>();
            for (int i = 0; i < concurrent; i++) {
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
                                    .content("{\"decision\":\"APPROVED\",\"memo\":\"C4 concurrent approval\"}"))
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

    private Fixture createFixture(String label) throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        String suffix = label + "-" + sequence;
        String investigatorLogin = "c4-inv-" + suffix;
        String reviewerLogin = "c4-rev-" + suffix;
        String caseKey = "C4-CASE-" + suffix;

        User investigator = userRepository.saveAndFlush(User.builder()
                .loginId(investigatorLogin)
                .email(investigatorLogin + "@test.local")
                .password(passwordEncoder.encode(PASSWORD))
                .name("C4 investigator")
                .organizationType(OrgType.POLICE)
                .department("C4 isolated")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
        User reviewer = userRepository.saveAndFlush(User.builder()
                .loginId(reviewerLogin)
                .email(reviewerLogin + "@test.local")
                .password(passwordEncoder.encode(PASSWORD))
                .name("C4 reviewer")
                .organizationType(OrgType.POLICE)
                .department("C4 isolated")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        Evidence evidence = evidenceRepository.saveAndFlush(Evidence.builder()
                .uploaderId(investigator.getUserId())
                .caseName(caseKey)
                .caseNumber(caseKey)
                .fileName("c4-" + suffix + ".mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue(String.format("%064x", sequence))
                .originalStoragePath("c4-fixture/" + suffix + ".mp4")
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
        result.setSummary("C4 isolated fixture");
        result.setAnalyzedAt(LocalDateTime.now());
        result = analysisResultRepository.saveAndFlush(result);

        CaseProfile profile = new CaseProfile(investigator.getUserId(), caseKey, evidence.getEvidenceId());
        profile.assignReviewer(reviewer.getUserId());
        profile = caseProfileRepository.saveAndFlush(profile);
        String token = JwtTestSupport.loginAndGetToken(mockMvc, reviewerLogin, PASSWORD);
        return new Fixture(caseKey, evidence.getEvidenceId(), result.getAnalysisResultId(),
                profile.getCaseProfileId(), reviewer.getUserId(), token);
    }

    private long countTasks(Long analysisResultId) {
        return taskRepository.findAll().stream()
                .filter(task -> analysisResultId.equals(task.getAnalysisResultId()))
                .count();
    }

    private void printTaskRows(String type, Long analysisResultId) {
        taskRepository.findAll().stream()
                .filter(task -> analysisResultId.equals(task.getAnalysisResultId()))
                .sorted(java.util.Comparator.comparing(task -> task.getReportIssueTaskId()))
                .forEach(task -> printResult(type,
                        "taskId=" + task.getReportIssueTaskId(),
                        "analysisResultId=" + task.getAnalysisResultId(),
                        "evidenceId=" + task.getEvidenceId(),
                        "status=" + task.getStatus(),
                        "requestedBy=" + task.getRequestedBy(),
                        "attemptCount=" + task.getAttemptCount()));
    }

    private String postgresVersion() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SHOW server_version");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getString(1);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private long countPdfFiles() {
        try (var files = Files.walk(UPLOAD_ROOT)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".pdf")).count();
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Path createUploadRoot() {
        try {
            return Files.createTempDirectory("forenshield-c4-before-");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private <T> T await(Future<T> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static long committed(List<TxResult> results) {
        return results.stream().filter(TxResult::committed).count();
    }

    private static long failed(List<TxResult> results) {
        return results.size() - committed(results);
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
            Long evidenceId,
            Long analysisResultId,
            Long caseProfileId,
            Long reviewerId,
            String reviewerToken
    ) {
    }

    private record TxResult(int workerId, boolean committed, String error) {
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
            byte[] response = ("{\"transactionHash\":\"0xc4-before-" + call
                    + "\",\"blockNumber\":" + (4000 + call) + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }
}
