package com.example.demo.service.analysis;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
class AnalysisAiResultIntegrationTest {

    @Autowired
    private AnalysisWorkerService analysisWorkerService;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private AnalysisModuleResultRepository analysisModuleResultRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private Evidence evidence;
    private AnalysisRequest queuedRequest;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .loginId("ai-worker")
                .email("ai-worker@test.local")
                .password(passwordEncoder.encode("password"))
                .name("AI Worker Test")
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(testUser.getUserId())
                .caseName("AI 테스트")
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc123def4567890abc123def4567890abc123def4567890abc123def4567890")
                .originalStoragePath("/tmp/sample.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        queuedRequest = new AnalysisRequest();
        queuedRequest.setEvidenceId(evidence.getEvidenceId());
        queuedRequest.setRequestedBy(testUser.getUserId());
        queuedRequest.setStatus(AnalysisStatus.QUEUED);
        queuedRequest.setProgressPercent(0);
        queuedRequest.setRequestedAt(LocalDateTime.now());
        queuedRequest = analysisRequestRepository.save(queuedRequest);
    }

    @AfterEach
    void tearDown() {
        analysisModuleResultRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void applyAiResult_persistsCompletedAnalysis() {
        AnalysisResponseMessage response = AnalysisResponseMessage.builder()
                .analysisRequestId(queuedRequest.getAnalysisRequestId())
                .evidenceId(evidence.getEvidenceId())
                .status("COMPLETED")
                .riskScore(88.0)
                .confidenceScore(0.95)
                .riskLevel("HIGH")
                .analysisReasons(List.of("Deepfake indicators detected."))
                .analyzedAt("2026-06-17T10:00:00Z")
                .results(List.of(AnalysisResponseMessage.AnalysisVideoResultItem.builder()
                        .type("video")
                        .deepfakeDetected(true)
                        .deepfakeScore(0.91)
                        .lipSyncDetected(false)
                        .frameEditDetected(true)
                        .frameEditScore(0.72)
                        .build()))
                .build();

        analysisWorkerService.applyAiResult(response);

        AnalysisRequest updated = analysisRequestRepository.findById(queuedRequest.getAnalysisRequestId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(updated.getProgressPercent()).isEqualTo(100);

        AnalysisResult result = analysisResultRepository.findByAnalysisRequestId(queuedRequest.getAnalysisRequestId())
                .orElseThrow();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.getRiskScore()).isEqualTo(88.0);
        assertThat(analysisModuleResultRepository.findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId()))
                .hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void applyAiResult_updatesExistingResultWithFrameRisks() {
        AnalysisResponseMessage initial = AnalysisResponseMessage.builder()
                .analysisRequestId(queuedRequest.getAnalysisRequestId())
                .evidenceId(evidence.getEvidenceId())
                .status("COMPLETED")
                .riskScore(11.0)
                .confidenceScore(0.83)
                .riskLevel("LOW")
                .analysisReasons(List.of("Initial GPU result without frame risks"))
                .analyzedAt("2026-07-03T07:27:36Z")
                .results(List.of(AnalysisResponseMessage.AnalysisVideoResultItem.builder()
                        .type("video")
                        .deepfakeDetected(false)
                        .deepfakeScore(0.11)
                        .build()))
                .build();

        analysisWorkerService.applyAiResult(initial);

        AnalysisResponseMessage updated = AnalysisResponseMessage.builder()
                .analysisRequestId(queuedRequest.getAnalysisRequestId())
                .evidenceId(evidence.getEvidenceId())
                .status("COMPLETED")
                .riskScore(10.8)
                .confidenceScore(0.84)
                .riskLevel("LOW")
                .analysisReasons(List.of("GPU frameRisks payload"))
                .analyzedAt("2026-07-03T07:27:37Z")
                .results(List.of(AnalysisResponseMessage.AnalysisVideoResultItem.builder()
                        .type("video")
                        .deepfakeDetected(false)
                        .deepfakeScore(0.108)
                        .frameRisks(List.of(
                                AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem.builder()
                                        .frameIndex(0)
                                        .timestampSec(0.0)
                                        .riskScore(0.12)
                                        .build(),
                                AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem.builder()
                                        .frameIndex(1)
                                        .timestampSec(2.1)
                                        .riskScore(0.18)
                                        .build()
                        ))
                        .build()))
                .build();

        analysisWorkerService.applyAiResult(updated);

        AnalysisResult result = analysisResultRepository.findByAnalysisRequestId(queuedRequest.getAnalysisRequestId())
                .orElseThrow();
        assertThat(result.getRiskScore()).isEqualTo(10.8);

        var timelineModule = analysisModuleResultRepository.findByAnalysisResultIdOrderByCreatedAtAsc(
                        result.getAnalysisResultId()).stream()
                .filter(module -> VideoAnalysisDetailsBuilder.MODULE_VIDEO_TIMELINE.equals(module.getModuleName()))
                .findFirst()
                .orElseThrow();
        assertThat(timelineModule.getDetailsJson()).contains("\"frameRisks\"");
        assertThat(timelineModule.getDetailsJson()).contains("\"riskScore\":0.12");
    }

    @Test
    void applyAiResult_persistsClipAndPairTimelines() {
        AnalysisResponseMessage response = AnalysisResponseMessage.builder()
                .analysisRequestId(queuedRequest.getAnalysisRequestId())
                .evidenceId(evidence.getEvidenceId())
                .status("COMPLETED")
                .riskScore(77.0)
                .confidenceScore(0.35)
                .riskLevel("HIGH")
                .analysisReasons(List.of("Late fusion timeline payload"))
                .analyzedAt("2026-07-07T01:00:00Z")
                .results(List.of(AnalysisResponseMessage.AnalysisVideoResultItem.builder()
                        .type("video")
                        .deepfakeDetected(true)
                        .deepfakeScore(0.77)
                        .frameRisks(List.of(
                                AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem.builder()
                                        .frameIndex(0)
                                        .timestampSec(0.0)
                                        .riskScore(0.86)
                                        .build()
                        ))
                        .clipRisks(List.of(
                                AnalysisResponseMessage.AnalysisVideoResultItem.ClipRiskItem.builder()
                                        .clipIndex(0)
                                        .startFrameIndex(0)
                                        .endFrameIndex(8)
                                        .startTimeSec(0.0)
                                        .endTimeSec(0.375)
                                        .riskScore(0.12)
                                        .build()
                        ))
                        .pairRisks(List.of(
                                AnalysisResponseMessage.AnalysisVideoResultItem.PairRiskItem.builder()
                                        .pairIndex(0)
                                        .frameIndexA(0)
                                        .frameIndexB(1)
                                        .timestampSec(0.04)
                                        .riskScore(0.34)
                                        .motionMagnitude(1.2)
                                        .build()
                        ))
                        .temporalSuspiciousSegments(List.of(
                                AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem.builder()
                                        .startTime(0.0)
                                        .endTime(0.5)
                                        .maxRiskScore(0.12)
                                        .reason("temporal high risk")
                                        .build()
                        ))
                        .opticalSuspiciousSegments(List.of(
                                AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem.builder()
                                        .startTime(0.0)
                                        .endTime(0.1)
                                        .maxRiskScore(0.34)
                                        .reason("optical high risk")
                                        .build()
                        ))
                        .representativeFrames(List.of(
                                AnalysisResponseMessage.AnalysisVideoResultItem.RepresentativeFrameItem.builder()
                                        .timeSec(0.4)
                                        .timestamp("00:00")
                                        .frameNumber(10)
                                        .score(0.86)
                                        .imageUrl("https://cdn.example/frame.jpg")
                                        .build()
                        ))
                        .overlayVideoUrl("https://cdn.example/overlay.mp4")
                        .build()))
                .build();

        analysisWorkerService.applyAiResult(response);

        AnalysisResult result = analysisResultRepository.findByAnalysisRequestId(queuedRequest.getAnalysisRequestId())
                .orElseThrow();
        var timelineModule = analysisModuleResultRepository.findByAnalysisResultIdOrderByCreatedAtAsc(
                        result.getAnalysisResultId()).stream()
                .filter(module -> VideoAnalysisDetailsBuilder.MODULE_VIDEO_TIMELINE.equals(module.getModuleName()))
                .findFirst()
                .orElseThrow();
        assertThat(timelineModule.getDetailsJson()).contains("\"clipRisks\"");
        assertThat(timelineModule.getDetailsJson()).contains("\"pairRisks\"");
        assertThat(timelineModule.getDetailsJson()).contains("\"temporalSuspiciousSegments\"");
        assertThat(timelineModule.getDetailsJson()).contains("\"opticalSuspiciousSegments\"");
        assertThat(timelineModule.getDetailsJson()).contains("\"representativeFrames\"");
        assertThat(timelineModule.getDetailsJson()).contains("\"overlayVideoUrl\":\"https://cdn.example/overlay.mp4\"");
        assertThat(timelineModule.getDetailsJson()).contains("\"riskScore\":0.12");
        assertThat(timelineModule.getDetailsJson()).contains("\"motionMagnitude\":1.2");
    }

    @Test
    void applyAiResult_marksFailedWhenAiReturnsFailed() {
        AnalysisResponseMessage response = AnalysisResponseMessage.builder()
                .analysisRequestId(queuedRequest.getAnalysisRequestId())
                .status("FAILED")
                .errorCode("GPU_OOM")
                .message("Worker ran out of memory")
                .build();

        analysisWorkerService.applyAiResult(response);

        AnalysisRequest updated = analysisRequestRepository.findById(queuedRequest.getAnalysisRequestId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(updated.getErrorCode()).isEqualTo("GPU_OOM");
    }

    @Test
    void applyAiResult_updatesProgressWhenInProgress() {
        analysisWorkerService.markDispatchedToAi(queuedRequest.getAnalysisRequestId());

        analysisWorkerService.applyAiResult(AnalysisResponseMessage.builder()
                .analysisRequestId(queuedRequest.getAnalysisRequestId())
                .evidenceId(queuedRequest.getEvidenceId())
                .status("IN_PROGRESS")
                .progressPercent(35)
                .message("Xception(CNN) 완료")
                .build());

        AnalysisRequest mid = analysisRequestRepository.findById(queuedRequest.getAnalysisRequestId()).orElseThrow();
        assertThat(mid.getStatus()).isEqualTo(AnalysisStatus.ANALYZING);
        assertThat(mid.getProgressPercent()).isEqualTo(35);

        analysisWorkerService.applyAiResult(AnalysisResponseMessage.builder()
                .analysisRequestId(queuedRequest.getAnalysisRequestId())
                .evidenceId(queuedRequest.getEvidenceId())
                .status("IN_PROGRESS")
                .progressPercent(20)
                .build());

        AnalysisRequest unchanged = analysisRequestRepository.findById(queuedRequest.getAnalysisRequestId()).orElseThrow();
        assertThat(unchanged.getProgressPercent()).isEqualTo(35);
    }
}
