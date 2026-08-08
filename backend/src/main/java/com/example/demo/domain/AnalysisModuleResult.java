package com.example.demo.domain;

import com.example.demo.domain.enums.FileType;
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
@Table(name = "analysis_module_results")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisModuleResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "module_result_id")
    private Long moduleResultId;

    @Column(name = "analysis_result_id", nullable = false)
    private Long analysisResultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", length = 20)
    private FileType fileType;

    @Column(name = "module_name", nullable = false, length = 100)
    private String moduleName;

    private Boolean detected;

    private Double score;

    private Double confidence;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "evidence_text", columnDefinition = "clob")
    private String evidenceText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "json")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
