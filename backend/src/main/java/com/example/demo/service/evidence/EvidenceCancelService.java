package com.example.demo.service.evidence;

import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Slf4j
@Service
public class EvidenceCancelService {

    private final EvidenceAccessService evidenceAccessService;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final CustodyLogService custodyLogService;
    private final S3Client s3Client;
    private final String evidenceBucket;

    public EvidenceCancelService(
            EvidenceAccessService evidenceAccessService,
            AnalysisRequestRepository analysisRequestRepository,
            CustodyLogService custodyLogService,
            S3Client s3Client,
            @Value("${aws.s3.evidence-bucket}") String evidenceBucket
    ) {
        this.evidenceAccessService = evidenceAccessService;
        this.analysisRequestRepository = analysisRequestRepository;
        this.custodyLogService = custodyLogService;
        this.s3Client = s3Client;
        this.evidenceBucket = evidenceBucket;
    }

    @Transactional
    public void resetEvidence(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        analysisRequestRepository.deleteByEvidenceId(evidenceId);
        deleteS3Object(evidence.getOriginalStoragePath());
        evidence.softDelete();
        custodyLogService.recordEvidenceAction(user, evidence, "EVIDENCE_DELETED", "초기화");
    }

    @Transactional
    public void cancelUpload(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);

        if (analysisRequestRepository.existsByEvidenceId(evidenceId)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "ANALYSIS_ALREADY_STARTED", "이미 분석이 시작된 증거는 취소할 수 없습니다.");
        }

        deleteS3Object(evidence.getOriginalStoragePath());
        evidence.softDelete();
        custodyLogService.recordEvidenceAction(user, evidence, "EVIDENCE_DELETED", evidence.getFileName());
    }

    private void deleteS3Object(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(evidenceBucket)
                    .key(s3Key)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", s3Key, e.getMessage());
        }
    }
}
