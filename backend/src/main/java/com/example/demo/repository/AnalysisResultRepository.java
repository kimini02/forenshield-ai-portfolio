package com.example.demo.repository;

import com.example.demo.domain.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByAnalysisRequestId(Long analysisRequestId);

    List<AnalysisResult> findByAnalysisRequestIdIn(Collection<Long> analysisRequestIds);

    List<AnalysisResult> findByAnalysisResultIdIn(Collection<Long> analysisResultIds);

    @Query("""
            SELECT COUNT(r)
            FROM AnalysisResult r
            JOIN AnalysisRequest ar ON ar.analysisRequestId = r.analysisRequestId
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE e.deletedAt IS NULL
              AND ar.status = com.example.demo.domain.enums.AnalysisStatus.COMPLETED
              AND r.riskScore IS NOT NULL
              AND r.riskScore >= :minScore
              AND r.riskScore <= :maxScore
            """)
    long countByRiskScoreRange(
            @Param("minScore") double minScore,
            @Param("maxScore") double maxScore
    );
}
