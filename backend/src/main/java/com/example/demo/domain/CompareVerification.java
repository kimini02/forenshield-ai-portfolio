package com.example.demo.domain;

import com.example.demo.domain.enums.CompareVerdict;
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
@Table(name = "compare_verifications")
@Getter
@Setter
@NoArgsConstructor
public class CompareVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compare_id")
    private Long compareId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_evidence_id", nullable = false)
    private Long originalEvidenceId;

    @Column(name = "candidate_file_name", nullable = false, length = 500)
    private String candidateFileName;

    @Column(name = "candidate_hash", nullable = false, length = 64)
    private String candidateHash;

    @Column(name = "candidate_file_size", nullable = false)
    private Long candidateFileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompareVerdict verdict;

    @Column(name = "match_count", nullable = false)
    private int matchCount;

    @Column(name = "mismatch_count", nullable = false)
    private int mismatchCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "json")
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
