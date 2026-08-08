package com.example.demo.support;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public final class EvidenceTestFixtures {

    public static final String DEFAULT_LOGIN_ID = "1111";
    public static final String DEFAULT_PASSWORD = "2222";
    public static final String DEFAULT_EMAIL = "1111@test.local";

    private EvidenceTestFixtures() {
    }

    public static User defaultApprovedUser(PasswordEncoder passwordEncoder) {
        return User.builder()
                .loginId(DEFAULT_LOGIN_ID)
                .email(DEFAULT_EMAIL)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .name("테스트 사용자")
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build();
    }

    public static MockMultipartFile videoMp4(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName, "video/mp4", content);
    }

    public static MockMultipartFile videoMp4(String fileName, String content) {
        return videoMp4(fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    public static Evidence videoEvidence(Long uploaderId, String fileName, char hashFill) {
        return videoEvidence(
                uploaderId,
                fileName,
                String.valueOf(hashFill).repeat(64),
                "original/" + fileName,
                100L,
                LocalDateTime.now()
        );
    }

    public static Evidence videoEvidence(
            Long uploaderId,
            String fileName,
            String originalHashValue,
            String originalStoragePath,
            long fileSize,
            LocalDateTime uploadedAt
    ) {
        return Evidence.builder()
                .uploaderId(uploaderId)
                .fileName(fileName)
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(fileSize)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue(originalHashValue)
                .originalStoragePath(originalStoragePath)
                .uploadedAt(uploadedAt)
                .build();
    }

    public static AnalysisRequest completedRequest(Long evidenceId, Long requestedBy, LocalDateTime completedAt) {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidenceId);
        request.setRequestedBy(requestedBy);
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setRequestedAt(completedAt.minusHours(1));
        request.setStartedAt(completedAt.minusMinutes(30));
        request.setCompletedAt(completedAt);
        request.setProgressPercent(100);
        return request;
    }

    public static AnalysisRequest analysisRequest(
            Evidence evidence,
            User user,
            AnalysisStatus status
    ) {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(user.getUserId());
        request.setStatus(status);
        request.setRequestedAt(LocalDateTime.now());
        request.setProgressPercent(status == AnalysisStatus.COMPLETED ? 100 : 0);
        return request;
    }

    public static AnalysisResult analysisResult(
            AnalysisRequest request,
            double riskScore,
            RiskLevel riskLevel
    ) {
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(riskScore);
        result.setConfidenceScore(0.9);
        result.setRiskLevel(riskLevel);
        result.setSummary("test");
        result.setAnalyzedAt(request.getCompletedAt());
        return result;
    }
}
