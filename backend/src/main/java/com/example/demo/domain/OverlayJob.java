package com.example.demo.domain;

import com.example.demo.domain.enums.OverlayJobStatus;
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
@Table(name = "overlay_jobs")
@Getter
@Setter
@NoArgsConstructor
public class OverlayJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "overlay_job_id")
    private Long overlayJobId;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "analysis_request_id", nullable = false)
    private Long analysisRequestId;

    @Column(name = "module", nullable = false, length = 40)
    private String module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OverlayJobStatus status = OverlayJobStatus.QUEUED;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent = 0;

    @Column(name = "overlay_video_url", length = 2000)
    private String overlayVideoUrl;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "clob")
    private String errorMessage;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
