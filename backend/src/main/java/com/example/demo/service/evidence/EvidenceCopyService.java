package com.example.demo.service.evidence;

import com.example.demo.service.custody.AnalysisCustodyLogService;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.CopyStatus;
import com.example.demo.exception.AnalysisCopyException;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceCopyService {

    private final S3Client s3Client;
    private final HashService hashService;
    private final EvidenceRepository evidenceRepository;
    private final AnalysisCustodyLogService analysisCustodyLogService;
    private final EvidenceManifestService evidenceManifestService;

    @Value("${aws.s3.evidence-bucket}")
    private String evidenceBucket;

    @Transactional
    public void prepareCopyForAnalysis(Evidence evidence, Long actorId) {
        if (evidence.getCopyStatus() == CopyStatus.ACTIVE && evidence.getCopyStoragePath() != null) {
            evidenceManifestService.ensureManifest(evidence);
            return;
        }

        String caseKey = EvidenceStoragePaths.resolveCaseKey(evidence);
        String copyKey = EvidenceStoragePaths.copyKey(caseKey, evidence, evidence.getFileName());

        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(evidenceBucket)
                    .sourceKey(evidence.getOriginalStoragePath())
                    .destinationBucket(evidenceBucket)
                    .destinationKey(copyKey)
                    .build());

            String copyHash = hashS3Object(copyKey);
            if (!copyHash.equals(evidence.getOriginalHashValue())) {
                throw new AnalysisCopyException(
                        AnalysisCustodyLogService.STEP_COPY_VERIFIED,
                        AnalysisCustodyLogService.ERR_COPY_VERIFY,
                        "원본·사본 SHA-256 해시가 일치하지 않습니다."
                );
            }

            evidence.activateCopy(copyKey, copyHash);
            evidenceRepository.save(evidence);
            evidenceManifestService.createAndSignManifest(evidence);
            analysisCustodyLogService.recordCopyCreated(actorId, evidence);
            analysisCustodyLogService.recordCopyVerified(actorId, evidence);
            log.info("Analysis copy prepared evidenceId={} copyKey={}", evidence.getEvidenceId(), copyKey);
        } catch (AnalysisCopyException ex) {
            analysisCustodyLogService.recordCopyError(
                    actorId, evidence, ex.getStep(), ex.getErrorCode(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            analysisCustodyLogService.recordCopyError(
                    actorId,
                    evidence,
                    AnalysisCustodyLogService.STEP_COPY_CREATED,
                    AnalysisCustodyLogService.ERR_COPY_CREATE,
                    "분석용 증거 사본 생성에 실패했습니다."
            );
            throw new AnalysisCopyException(
                    AnalysisCustodyLogService.STEP_COPY_CREATED,
                    AnalysisCustodyLogService.ERR_COPY_CREATE,
                    "분석용 증거 사본 생성에 실패했습니다.",
                    ex
            );
        }
    }

    @Transactional
    public void deleteAnalysisCopy(Evidence evidence, Long actorId) {
        if (evidence.getCopyStatus() != CopyStatus.ACTIVE || evidence.getCopyStoragePath() == null) {
            return;
        }

        String deletedCopyPath = evidence.getCopyStoragePath();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(evidenceBucket)
                    .key(deletedCopyPath)
                    .build());
            evidence.markCopyDeleted();
            evidenceRepository.save(evidence);
            analysisCustodyLogService.recordCopyDeleted(actorId, evidence, deletedCopyPath);
            log.info("Analysis copy deleted evidenceId={} copyKey={}", evidence.getEvidenceId(), deletedCopyPath);
        } catch (Exception ex) {
            analysisCustodyLogService.recordCopyError(
                    actorId,
                    evidence,
                    AnalysisCustodyLogService.STEP_COPY_DELETED,
                    AnalysisCustodyLogService.ERR_COPY_DELETE,
                    "분석용 증거 사본 삭제에 실패했습니다."
            );
            log.warn("Failed to delete analysis copy evidenceId={}", evidence.getEvidenceId(), ex);
        }
    }

    private String hashS3Object(String objectKey) {
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(evidenceBucket)
                        .key(objectKey)
                        .build())) {
            return hashService.generateSha256(stream);
        } catch (Exception ex) {
            throw new AnalysisCopyException(
                    AnalysisCustodyLogService.STEP_COPY_VERIFIED,
                    AnalysisCustodyLogService.ERR_COPY_VERIFY,
                    "사본 SHA-256 해시 계산에 실패했습니다.",
                    ex
            );
        }
    }
}
