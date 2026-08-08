package com.example.demo.domain;

import com.example.demo.domain.enums.ReportPublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "analysis_result_id")
    private Long analysisResultId;

    @Column(name = "compare_id")
    private Long compareId;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "report_file_name", length = 500)
    private String reportFileName;

    @Column(name = "report_no", length = 40)
    private String reportNo;

    @Column(name = "verification_token", length = 100)
    private String verificationToken;

    @Column(name = "verification_code", length = 30)
    private String verificationCode;

    @Column(name = "public_access_code", length = 30)
    private String publicAccessCode;

    @Column(name = "public_access_enabled", nullable = false)
    private Boolean publicAccessEnabled = false;

    @Column(name = "public_access_issued_at")
    private LocalDateTime publicAccessIssuedAt;

    @Column(name = "public_access_expires_at")
    private LocalDateTime publicAccessExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "publication_status",
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) default 'ISSUED'"
    )
    private ReportPublicationStatus publicationStatus = ReportPublicationStatus.DRAFT;

    @Column(name = "report_version", nullable = false, columnDefinition = "integer default 1")
    private Integer reportVersion = 1;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Column(name = "storage_path", nullable = false, columnDefinition = "clob")
    private String storagePath;

    @Column(name = "report_hash", length = 64)
    private String reportHash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isIssued() {
        return publicationStatus == ReportPublicationStatus.ISSUED;
    }

    public void markIssued(Long issuerId, LocalDateTime issuedAt) {
        this.publicationStatus = ReportPublicationStatus.ISSUED;
        this.issuedBy = issuerId;
        this.issuedAt = issuedAt;
        this.supersededAt = null;
    }

    public void markSuperseded(LocalDateTime supersededAt) {
        this.publicationStatus = ReportPublicationStatus.SUPERSEDED;
        this.supersededAt = supersededAt;
        this.publicAccessEnabled = false;
    }
}
