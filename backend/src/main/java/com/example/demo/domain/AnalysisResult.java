package com.example.demo.domain;

import com.example.demo.domain.enums.RiskLevel;
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
@Table(name = "analysis_results")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_result_id")
    private Long analysisResultId;

    @Column(name = "analysis_request_id", nullable = false, unique = true)
    private Long analysisRequestId;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(columnDefinition = "clob")
    private String summary;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
}
