package com.example.demo.service.report;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
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
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.ReportIssueTaskRepository;
import com.example.demo.repository.ReportPublicationSnapshotRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.blockchain.client.BlockchainAnchorClient;
import com.example.demo.service.blockchain.client.BlockchainAnchorResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
        "report.issue.worker.enabled=false",
        "file.upload-dir=build/test-uploads/c3-worker"
})
class ReportIssueWorkerIntegrationTest {

    @Autowired
    private ReportIssueTaskProcessor processor;
    @Autowired
    private ReportIssueTaskStateService taskStateService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EvidenceRepository evidenceRepository;
    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;
    @Autowired
    private AnalysisResultRepository analysisResultRepository;
    @Autowired
    private CaseProfileRepository caseProfileRepository;
    @Autowired
    private ReportIssueTaskRepository taskRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ReportPublicationSnapshotRepository snapshotRepository;
    @Autowired
    private BlockchainAnchorRepository anchorRepository;
    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @MockBean
    private BlockchainAnchorClient blockchainAnchorClient;
    @MockBean
    private ReportContentBuilder reportContentBuilder;

    @SpyBean
    private ReportIssuePersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        reset(blockchainAnchorClient, reportContentBuilder, persistenceService);
        custodyLogRepository.deleteAll();
        anchorRepository.deleteAll();
        snapshotRepository.deleteAll();
        reportRepository.deleteAll();
        taskRepository.deleteAll();
        caseProfileRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
        when(reportContentBuilder.buildEvidenceLines(any(), any(), any(), any()))
                .thenReturn(List.of("Risk Level: MEDIUM", "Analyzed At: 2026-08-11T00:00:00Z"));
    }

    @Test
    void worker_issuesReportAnchorsAndCompletesTask() {
        Fixture fixture = fixture();
        when(blockchainAnchorClient.anchor(any()))
                .thenReturn(new BlockchainAnchorResult("tx-success", 10L, true, null));

        processor.process(fixture.taskId());

        ReportIssueTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        Report report = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()).orElseThrow();
        var anchor = anchorRepository.findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                report.getReportId(), BlockchainAnchorType.REPORT_HASH).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
        assertThat(report.isIssued()).isTrue();
        assertThat(java.nio.file.Files.isRegularFile(java.nio.file.Path.of(report.getStoragePath()))).isTrue();
        assertThat(snapshotRepository.findByReportId(report.getReportId())).isPresent();
        assertThat(anchor.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
    }

    @Test
    void worker_explicitBlockchainFailureLeavesIssuedReportAndCompletesTask() {
        Fixture fixture = fixture();
        when(blockchainAnchorClient.anchor(any()))
                .thenReturn(new BlockchainAnchorResult(null, null, false, "explicit gateway failure"));

        processor.process(fixture.taskId());

        ReportIssueTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        Report report = reportRepository
                .findTopByAnalysisResultIdOrderByCreatedAtDesc(fixture.analysisResultId()).orElseThrow();
        var anchor = anchorRepository.findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                report.getReportId(), BlockchainAnchorType.REPORT_HASH).orElseThrow();
        assertThat(report.isIssued()).isTrue();
        assertThat(anchor.getStatus()).isEqualTo(BlockchainAnchorStatus.FAILED);
        assertThat(anchor.getErrorCode()).isEqualTo("FABRIC_SUBMIT_FAILED");
        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.COMPLETED);
    }

    @Test
    void worker_externalWaitHasNoActiveSpringTransaction() {
        Fixture fixture = fixture();
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        when(blockchainAnchorClient.anchor(any())).thenAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            Thread.sleep(100L);
            return new BlockchainAnchorResult("tx-delayed", 11L, true, null);
        });

        processor.process(fixture.taskId());

        assertThat(transactionActive).isFalse();
        assertThat(taskRepository.findById(fixture.taskId()).orElseThrow().getStatus())
                .isEqualTo(ReportIssueTaskStatus.COMPLETED);
    }

    @Test
    void worker_reportPersistenceFailureKeepsApprovalAndSchedulesRetryWithArtifactPath() {
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("forced report persistence failure"))
                .when(persistenceService).persistIssuedReport(any(), any());

        processor.process(fixture.taskId());

        ReportIssueTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(caseProfileRepository.findById(fixture.caseProfileId()).orElseThrow().getReviewStatus())
                .isEqualTo(CaseReviewStatus.REPORT_APPROVED);
        assertThat(task.getStatus()).isEqualTo(ReportIssueTaskStatus.PENDING);
        assertThat(task.getArtifactPath()).isNotBlank();
        assertThat(java.nio.file.Files.isRegularFile(java.nio.file.Path.of(task.getArtifactPath()))).isTrue();
        assertThat(task.getLastError()).contains("forced report persistence failure", "artifactPath=");
        assertThat(reportRepository.count()).isZero();
    }

    @Test
    void workerClaimQuery_isAlsoExecutableInH2TestMode() {
        ReportIssueTask pending = taskRepository.saveAndFlush(ReportIssueTask.pending(1L, 2L, 3L, 4L));

        List<Long> claimed = taskStateService.claimBatch();

        assertThat(claimed).containsExactly(pending.getReportIssueTaskId());
        assertThat(taskRepository.findById(pending.getReportIssueTaskId()).orElseThrow().getStatus())
                .isEqualTo(ReportIssueTaskStatus.PROCESSING);
    }

    private Fixture fixture() {
        User investigator = userRepository.save(User.builder()
                .loginId("worker-inv-" + System.nanoTime())
                .email("worker-inv-" + System.nanoTime() + "@test.local")
                .password("encoded")
                .name("worker investigator")
                .organizationType(OrgType.POLICE)
                .department("worker-test")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
        User reviewer = userRepository.save(User.builder()
                .loginId("worker-rev-" + System.nanoTime())
                .email("worker-rev-" + System.nanoTime() + "@test.local")
                .password("encoded")
                .name("worker reviewer")
                .organizationType(OrgType.POLICE)
                .department("worker-test")
                .role(UserRole.ROLE_REVIEWER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
        String caseKey = "worker-case-" + System.nanoTime();
        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(investigator.getUserId())
                .caseName(caseKey)
                .caseNumber(caseKey)
                .fileName("worker.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("b".repeat(64))
                .originalStoragePath("isolated/worker.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(investigator.getUserId());
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setProgressPercent(100);
        request.setRequestedAt(LocalDateTime.now().minusMinutes(1));
        request.setCompletedAt(LocalDateTime.now());
        request = analysisRequestRepository.save(request);
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(60.0);
        result.setConfidenceScore(0.8);
        result.setRiskLevel(RiskLevel.MEDIUM);
        result.setSummary("worker test");
        result.setAnalyzedAt(LocalDateTime.now());
        result = analysisResultRepository.save(result);
        CaseProfile profile = new CaseProfile(investigator.getUserId(), caseKey, evidence.getEvidenceId());
        profile.assignReviewer(reviewer.getUserId());
        profile.approveReview();
        profile = caseProfileRepository.save(profile);
        assertThat(profile.getReviewStatus()).isEqualTo(CaseReviewStatus.REPORT_APPROVED);
        ReportIssueTask task = ReportIssueTask.pending(
                profile.getCaseProfileId(), evidence.getEvidenceId(), result.getAnalysisResultId(), reviewer.getUserId());
        task.claim(LocalDateTime.now());
        task = taskRepository.save(task);
        return new Fixture(task.getReportIssueTaskId(), result.getAnalysisResultId(), profile.getCaseProfileId());
    }

    private record Fixture(Long taskId, Long analysisResultId, Long caseProfileId) {
    }
}
