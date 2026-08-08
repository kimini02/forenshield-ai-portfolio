package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "report_publication_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportPublicationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "report_id", nullable = false, unique = true)
    private Long reportId;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "pdf_template_version", nullable = false, length = 40)
    private String pdfTemplateVersion;

    @Column(name = "report_input_json", nullable = false, columnDefinition = "text")
    private String reportInputJson;

    @Column(name = "public_summary_json", nullable = false, columnDefinition = "text")
    private String publicSummaryJson;

    @Column(name = "artifact_manifest_json", nullable = false, columnDefinition = "text")
    private String artifactManifestJson;

    @Column(name = "display_policy_json", nullable = false, columnDefinition = "text")
    private String displayPolicyJson;

    @Column(name = "snapshot_hash", nullable = false, length = 64)
    private String snapshotHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ReportPublicationSnapshot create(
            Long reportId,
            String schemaVersion,
            String pdfTemplateVersion,
            String reportInputJson,
            String publicSummaryJson,
            String artifactManifestJson,
            String displayPolicyJson,
            String snapshotHash,
            LocalDateTime createdAt
    ) {
        ReportPublicationSnapshot snapshot = new ReportPublicationSnapshot();
        snapshot.reportId = reportId;
        snapshot.schemaVersion = schemaVersion;
        snapshot.pdfTemplateVersion = pdfTemplateVersion;
        snapshot.reportInputJson = reportInputJson;
        snapshot.publicSummaryJson = publicSummaryJson;
        snapshot.artifactManifestJson = artifactManifestJson;
        snapshot.displayPolicyJson = displayPolicyJson;
        snapshot.snapshotHash = snapshotHash;
        snapshot.createdAt = createdAt;
        return snapshot;
    }
}
