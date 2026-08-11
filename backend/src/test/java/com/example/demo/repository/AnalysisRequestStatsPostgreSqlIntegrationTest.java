package com.example.demo.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnalysisRequestStatsPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("forenshield_c2_stats")
            .withUsername("forenshield")
            .withPassword("forenshield")
            .withInitScript("db/test/postgresql-domains.sql");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Test
    void aggregatesDashboardStatsWithExistingConditions() {
        User owner = saveUser("stats-owner");
        User other = saveUser("stats-other");

        saveCompleted(owner, RiskLevel.HIGH, false, "high.mp4");
        saveCompleted(owner, RiskLevel.MEDIUM, false, "medium.mp4");
        saveCompleted(owner, RiskLevel.LOW, false, "low.mp4");
        saveRequest(owner, AnalysisStatus.COMPLETED, false, "no-result.mp4");
        saveRequest(owner, AnalysisStatus.QUEUED, false, "queued.mp4");
        saveRequest(owner, AnalysisStatus.ANALYZING, false, "analyzing.mp4");
        saveRequest(owner, AnalysisStatus.FAILED, false, "failed.mp4");
        saveCompleted(owner, RiskLevel.HIGH, true, "deleted.mp4");
        saveCompleted(other, RiskLevel.HIGH, false, "other-owner.mp4");

        DashboardStatsProjection stats = analysisRequestRepository
                .findDashboardStatsByUploader(owner.getUserId());

        assertThat(stats.getTotalAnalysisCount()).isEqualTo(7);
        assertThat(stats.getDeepfakeDetectedCount()).isEqualTo(2);
        assertThat(stats.getCompletedCount()).isEqualTo(4);
        assertThat(stats.getInProgressCount()).isEqualTo(2);
    }

    private void saveCompleted(
            User owner,
            RiskLevel riskLevel,
            boolean deleted,
            String fileName
    ) {
        AnalysisRequest request = saveRequest(
                owner, AnalysisStatus.COMPLETED, deleted, fileName
        );
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(80.0);
        result.setConfidenceScore(0.9);
        result.setRiskLevel(riskLevel);
        result.setSummary("stats test");
        result.setAnalyzedAt(at(2));
        analysisResultRepository.saveAndFlush(result);
    }

    private AnalysisRequest saveRequest(
            User owner,
            AnalysisStatus status,
            boolean deleted,
            String fileName
    ) {
        Evidence evidence = evidenceRepository.saveAndFlush(Evidence.builder()
                .uploaderId(owner.getUserId())
                .fileName(fileName)
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(10L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue(Integer.toHexString(fileName.hashCode()).repeat(8))
                .originalStoragePath("test/" + fileName)
                .uploadedAt(at(1))
                .build());
        if (deleted) {
            evidence.softDelete();
            evidenceRepository.saveAndFlush(evidence);
        }

        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(owner.getUserId());
        request.setStatus(status);
        request.setRequestedAt(at(1));
        request.setStartedAt(status == AnalysisStatus.QUEUED ? null : at(1));
        request.setCompletedAt(status == AnalysisStatus.COMPLETED ? at(2) : null);
        request.setProgressPercent(status == AnalysisStatus.COMPLETED ? 100 : 0);
        return analysisRequestRepository.saveAndFlush(request);
    }

    private User saveUser(String loginId) {
        return userRepository.saveAndFlush(User.builder()
                .loginId(loginId)
                .email(loginId + "@test.local")
                .password("encoded")
                .name(loginId)
                .organizationType(OrgType.ETC)
                .department("test")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());
    }

    private LocalDateTime at(int minute) {
        return LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(minute);
    }
}
