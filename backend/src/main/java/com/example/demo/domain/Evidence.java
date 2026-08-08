package com.example.demo.domain;

import com.example.demo.domain.enums.CopyStatus;
import com.example.demo.domain.enums.EvidenceLifecycleStatus;
import com.example.demo.domain.enums.EvidenceRole;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

    public static final String HASH_ALGORITHM_SHA256 = "SHA-256";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id")
    private Long evidenceId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "case_number", length = 100)
    private String caseNumber;

    @Column(name = "case_name", length = 255)
    private String caseName;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private FileType fileType;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "hash_algorithm", nullable = false, length = 20)
    private String hashAlgorithm;

    @Column(name = "original_hash_value", nullable = false, length = 64)
    private String originalHashValue;

    @Column(name = "original_storage_path", nullable = false, columnDefinition = "clob")
    private String originalStoragePath;

    @Column(name = "copy_hash_value", length = 64)
    private String copyHashValue;

    @Column(name = "copy_storage_path", columnDefinition = "clob")
    private String copyStoragePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "copy_status", nullable = false, length = 20)
    private CopyStatus copyStatus;

    @Column(name = "copy_created_at")
    private LocalDateTime copyCreatedAt;

    @Column(name = "copy_deleted_at")
    private LocalDateTime copyDeletedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private EvidenceLifecycleStatus lifecycleStatus = EvidenceLifecycleStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_role", length = 20)
    private EvidenceRole evidenceRole;

    @Column(name = "display_label", length = 100)
    private String displayLabel;

    @Column(name = "replacement_evidence_id")
    private Long replacementEvidenceId;

    @Column(name = "excluded_reason", length = 500)
    private String excludedReason;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Evidence(
            Long uploaderId,
            String caseNumber,
            String caseName,
            String fileName,
            FileType fileType,
            String mimeType,
            Long fileSize,
            String hashAlgorithm,
            String originalHashValue,
            String originalStoragePath,
            LocalDateTime uploadedAt
    ) {
        this.uploaderId = uploaderId;
        this.caseNumber = caseNumber;
        this.caseName = caseName;
        this.fileName = fileName;
        this.fileType = fileType;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.hashAlgorithm = hashAlgorithm;
        this.originalHashValue = originalHashValue;
        this.originalStoragePath = originalStoragePath;
        this.copyStatus = CopyStatus.NONE;
        this.status = EvidenceStatus.UPLOADED;
        this.lifecycleStatus = EvidenceLifecycleStatus.ACTIVE;
        this.uploadedAt = uploadedAt;
    }

    /** API/테스트 호환용 — ERD 컬럼명은 originalHashValue */
    public String getHashValue() {
        return originalHashValue;
    }

    public void softDelete() {
        this.status = EvidenceStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void updateCaseInfo(String caseName) {
        this.caseName = caseName;
    }

    public void renameCase(String oldCaseKey, String newCaseName) {
        this.caseName = newCaseName;
        if (this.caseNumber != null && this.caseNumber.equalsIgnoreCase(oldCaseKey)) {
            this.caseNumber = newCaseName;
        }
    }

    public void updateOriginalStoragePath(String path) {
        this.originalStoragePath = path;
    }

    public void activateCopy(String copyStoragePath, String copyHashValue) {
        this.copyStoragePath = copyStoragePath;
        this.copyHashValue = copyHashValue;
        this.copyStatus = CopyStatus.ACTIVE;
        this.copyCreatedAt = LocalDateTime.now();
        this.copyDeletedAt = null;
    }

    public void markCopyDeleted() {
        this.copyStatus = CopyStatus.DELETED;
        this.copyDeletedAt = LocalDateTime.now();
    }

    public void exclude(String reason) {
        this.lifecycleStatus = EvidenceLifecycleStatus.EXCLUDED;
        this.excludedReason = reason;
        this.evidenceRole = EvidenceRole.SUPPLEMENT;
    }

    public void markReplaced(Long replacementEvidenceId, String reason) {
        this.lifecycleStatus = EvidenceLifecycleStatus.REPLACED;
        this.replacementEvidenceId = replacementEvidenceId;
        this.excludedReason = reason;
    }

    public void assignRole(EvidenceRole role) {
        this.evidenceRole = role;
    }

    public void assignDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public boolean isWorkflowActive() {
        return lifecycleStatus == EvidenceLifecycleStatus.ACTIVE;
    }
}
