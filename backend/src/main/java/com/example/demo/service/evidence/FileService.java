package com.example.demo.service.evidence;

import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.service.readiness.EvidenceReadinessService;
import com.example.demo.service.evidence.hls.EvidenceHlsSeedService;
import com.example.demo.domain.Evidence;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.dto.MediaMetadata;
import com.example.demo.dto.ValidatedFile;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.HashGenerationException;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.EvidenceCaseIdResolver;
import com.example.demo.util.JsonPayloadWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private final Path root;
    private final S3Client s3Client;
    private final String evidenceBucket;
    private final MediaService mediaService;
    private final HashService hashService;
    private final EvidenceRepository evidenceRepository;
    private final CaseProfileRepository caseProfileRepository;
    private final FileValidationService fileValidationService;
    private final CustodyLogService custodyLogService;
    private final EvidenceMetadataService evidenceMetadataService;
    private final CaseEvidencePresentationService caseEvidencePresentationService;
    private final JsonPayloadWriter jsonPayloadWriter;
    private final BlockchainAnchorService blockchainAnchorService;
    private final EvidenceReadinessService evidenceReadinessService;
    private final EvidenceHlsSeedService evidenceHlsSeedService;

    public FileService(
            @Value("${file.upload-dir:uploads}") String uploadDir,
            @Value("${aws.s3.evidence-bucket}") String evidenceBucket,
            S3Client s3Client,
            MediaService mediaService,
            HashService hashService,
            EvidenceRepository evidenceRepository,
            CaseProfileRepository caseProfileRepository,
            FileValidationService fileValidationService,
            CustodyLogService custodyLogService,
            EvidenceMetadataService evidenceMetadataService,
            CaseEvidencePresentationService caseEvidencePresentationService,
            JsonPayloadWriter jsonPayloadWriter,
            BlockchainAnchorService blockchainAnchorService,
            EvidenceReadinessService evidenceReadinessService,
            EvidenceHlsSeedService evidenceHlsSeedService
    ) {
        this.s3Client = s3Client;
        this.evidenceBucket = evidenceBucket;
        this.root = Paths.get(uploadDir);
        this.mediaService = mediaService;
        this.hashService = hashService;
        this.evidenceRepository = evidenceRepository;
        this.caseProfileRepository = caseProfileRepository;
        this.fileValidationService = fileValidationService;
        this.custodyLogService = custodyLogService;
        this.evidenceMetadataService = evidenceMetadataService;
        this.caseEvidencePresentationService = caseEvidencePresentationService;
        this.jsonPayloadWriter = jsonPayloadWriter;
        this.blockchainAnchorService = blockchainAnchorService;
        this.evidenceReadinessService = evidenceReadinessService;
        this.evidenceHlsSeedService = evidenceHlsSeedService;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize folder for upload!", e);
        }
    }

    @Transactional
    public FileUploadResponse upload(MultipartFile file, String caseName, String caseNumber, Long uploaderId) {
        if (caseName == null || caseName.isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사건명을 입력해 주세요.");
        }

        String trimmedCaseName = caseName.trim();
        String trimmedCaseNumber = resolveCaseNumber(caseNumber, trimmedCaseName);
        ValidatedFile validated = fileValidationService.validate(file);
        String originalFilename = validated.fileName();

        try {
            Files.createDirectories(this.root);
            String storedFileName = UUID.randomUUID() + "_" + originalFilename;
            Path savedPath = this.root.resolve(storedFileName);
            // 저장(Write)과 SHA-256 계산을 한 패스로 수행 — 디스크 재읽기 없이 해시 확정
            String hashValue = hashService.saveAndGenerateSha256(file, savedPath);

            MediaMetadata extracted = null;
            ExtractionStatus extractionStatus = ExtractionStatus.SUCCESS;
            String extractionError = null;
            try {
                extracted = mediaService.extractMetadata(savedPath);
                if (extracted.getWidth() == null && extracted.getCodec() == null) {
                    extractionStatus = ExtractionStatus.PARTIAL;
                }
            } catch (Exception e) {
                log.error("Metadata extraction failed for {}: {}", originalFilename, e.getMessage());
                extractionStatus = ExtractionStatus.FAILED;
                extractionError = e.getMessage();
            }

            Evidence evidence = Evidence.builder()
                    .uploaderId(uploaderId)
                    .caseName(trimmedCaseName)
                    .caseNumber(trimmedCaseNumber)
                    .fileName(originalFilename)
                    .fileType(validated.fileType())
                    .mimeType(validated.mimeType())
                    .fileSize(validated.fileSize())
                    .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                    .originalHashValue(hashValue)
                    .originalStoragePath("pending")
                    .uploadedAt(LocalDateTime.now())
                    .build();

            Evidence savedEvidence = evidenceRepository.save(evidence);

            String caseKey = EvidenceStoragePaths.resolveCaseKey(savedEvidence);
            String s3Key = EvidenceStoragePaths.originalKey(caseKey, savedEvidence, originalFilename);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(evidenceBucket)
                            .key(s3Key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromFile(savedPath));

            savedEvidence.updateOriginalStoragePath(s3Key);
            evidenceRepository.save(savedEvidence);

            evidenceMetadataService.saveFromExtraction(
                    savedEvidence.getEvidenceId(), extracted, extractionStatus, extractionError);
            // 업로드 시 즉시 readiness seed + 응답에 포함
            var readinessSnapshot = evidenceReadinessService.seedFfprobeReadiness(savedEvidence.getEvidenceId());

            if (validated.fileType() == FileType.VIDEO) {
                evidenceHlsSeedService.seedPendingAndEnqueue(
                        savedEvidence.getEvidenceId(),
                        validated.fileType()
                );
            }

            recordUploadCustodyLogs(savedEvidence, uploaderId, extractionStatus.name());
            blockchainAnchorService.anchorEvidenceHash(savedEvidence, uploaderId);

            Files.deleteIfExists(savedPath);

            List<Evidence> caseEvidences = evidenceRepository.findByUploaderIdAndCaseKey(
                    uploaderId, trimmedCaseName);
            String displayLabel = caseEvidencePresentationService.resolveDisplayLabel(
                    savedEvidence, caseEvidences);
            if (savedEvidence.getDisplayLabel() == null || savedEvidence.getDisplayLabel().isBlank()) {
                savedEvidence.assignDisplayLabel(displayLabel);
                evidenceRepository.save(savedEvidence);
            }

            reopenAssignedReview(savedEvidence);

            String originalSha256 = savedEvidence.getOriginalHashValue();
            return FileUploadResponse.builder()
                    .success(true)
                    .message("파일 업로드 완료")
                    .evidenceId(savedEvidence.getEvidenceId())
                    .fileName(originalFilename)
                    .caseName(savedEvidence.getCaseName())
                    .caseNumber(savedEvidence.getCaseNumber())
                    .displayLabel(displayLabel)
                    .fileSize(validated.fileSize())
                    .hashAlgorithm(savedEvidence.getHashAlgorithm())
                    .hashValue(originalSha256)
                    .originalSha256(originalSha256)
                    .metadata(toMetadataResponse(extracted, extractionStatus, extractionError))
                    .readiness(evidenceReadinessService.toResponse(
                            savedEvidence.getEvidenceId(), readinessSnapshot))
                    .build();
        } catch (HashGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("FileUpload Error: ", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "파일 업로드에 실패했습니다.");
        }
    }

    private Object toMetadataResponse(MediaMetadata extracted, ExtractionStatus status, String error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("extractionStatus", status.name());
        if (error != null) {
            response.put("message", error);
        }
        if (extracted != null) {
            response.put("type", extracted.getType());
            response.put("width", extracted.getWidth());
            response.put("height", extracted.getHeight());
            response.put("durationSec", extracted.getDuration());
            response.put("fps", extracted.getFps());
            response.put("codec", extracted.getCodec());
            response.put("sampleRate", extracted.getSampleRate());
            response.put("channels", extracted.getChannels());
            response.put("hasAudioTrack", extracted.getHasAudioTrack());
        }
        return response;
    }

    private void reopenAssignedReview(Evidence evidence) {
        String caseKey = EvidenceCaseIdResolver.resolve(evidence);
        caseProfileRepository.findByUploaderIdAndCaseKey(evidence.getUploaderId(), caseKey)
                .ifPresent(profile -> {
                    profile.reopenReviewForNewEvidence();
                    caseProfileRepository.save(profile);
                });
    }

    private void recordUploadCustodyLogs(Evidence savedEvidence, Long uploaderId, String extractionStatus) {
        custodyLogService.record(
                uploaderId,
                CustodyTargetType.EVIDENCE,
                savedEvidence.getEvidenceId(),
                "EVIDENCE_UPLOADED",
                savedEvidence.getOriginalHashValue(),
                savedEvidence.getOriginalStoragePath(),
                "증거 파일 업로드 완료",
                jsonPayloadWriter.toJson(uploadPayload(savedEvidence)),
                null
        );

        custodyLogService.record(
                uploaderId,
                CustodyTargetType.EVIDENCE,
                savedEvidence.getEvidenceId(),
                "HASH_CREATED",
                savedEvidence.getOriginalHashValue(),
                savedEvidence.getOriginalStoragePath(),
                "SHA-256 해시 생성 완료",
                jsonPayloadWriter.toJson(hashPayload(savedEvidence)),
                null
        );

        custodyLogService.record(
                uploaderId,
                CustodyTargetType.EVIDENCE,
                savedEvidence.getEvidenceId(),
                "METADATA_EXTRACTED",
                savedEvidence.getOriginalHashValue(),
                savedEvidence.getOriginalStoragePath(),
                "메타데이터 추출 완료",
                jsonPayloadWriter.toJson(metadataPayload(extractionStatus)),
                null
        );
    }

    private Map<String, Object> uploadPayload(Evidence evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileName", evidence.getFileName());
        payload.put("fileType", evidence.getFileType().name());
        payload.put("mimeType", evidence.getMimeType());
        payload.put("fileSize", evidence.getFileSize());
        payload.put("caseName", evidence.getCaseName());
        payload.put("caseNumber", evidence.getCaseNumber());
        return payload;
    }

    private static String resolveCaseNumber(String caseNumber, String caseName) {
        if (caseNumber != null && !caseNumber.isBlank()) {
            return caseNumber.trim();
        }
        return caseName;
    }

    private Map<String, Object> hashPayload(Evidence evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hashAlgorithm", evidence.getHashAlgorithm());
        payload.put("hashValue", evidence.getOriginalHashValue());
        return payload;
    }

    private Map<String, Object> metadataPayload(String extractionStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("extractionStatus", extractionStatus);
        return payload;
    }
}
