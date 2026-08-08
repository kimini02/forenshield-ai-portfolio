package com.example.demo.domain;

import com.example.demo.domain.enums.ExtractionStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_metadata")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metadata_id")
    private Long metadataId;

    @Column(name = "evidence_id", nullable = false, unique = true)
    private Long evidenceId;

    private Integer width;

    private Integer height;

    @Column(name = "duration_sec")
    private Integer durationSec;

    private Double fps;

    @Column(length = 100)
    private String codec;

    @Column(name = "sample_rate")
    private Integer sampleRate;

    private Integer channels;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exif_json", columnDefinition = "json")
    private String exifJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ffprobe_json", columnDefinition = "json")
    private String ffprobeJson;

    // video_readiness.py 프레임 샘플링 후 갱신
    @JdbcTypeCode(SqlTypes.JSON) // readiness_json 산출 경로.
    @Column(name = "readiness_json", columnDefinition = "json") // JSON 형식으로 저장
    private String readinessJson; // 등급(GOOD/CAUTION/POOR/BLOCK) + JSON

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 20)
    private ExtractionStatus extractionStatus = ExtractionStatus.FAILED;

    @Column(name = "extraction_error", columnDefinition = "clob")
    private String extractionError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
