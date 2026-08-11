package com.example.demo.repository;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.enums.AnalysisStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, Long> {

    List<AnalysisRequest> findByEvidenceIdInOrderByRequestedAtDesc(List<Long> evidenceIds);

    List<AnalysisRequest> findByEvidenceIdInOrderByRequestedAtDescAnalysisRequestIdDesc(List<Long> evidenceIds);

    List<AnalysisRequest> findByEvidenceIdOrderByRequestedAtDesc(Long evidenceId);

    boolean existsByEvidenceId(Long evidenceId);

    boolean existsByEvidenceIdAndStatus(Long evidenceId, AnalysisStatus status);

    boolean existsByEvidenceIdAndStatusIn(Long evidenceId, List<AnalysisStatus> statuses);

    Optional<AnalysisRequest> findTopByEvidenceIdOrderByRequestedAtDesc(Long evidenceId);

    void deleteByEvidenceId(Long evidenceId);

    @Query(value = """
            SELECT
                COUNT(*) AS "totalAnalysisCount",
                COUNT(*) FILTER (
                    WHERE ar.status = 'COMPLETED'
                      AND r.risk_level IN ('HIGH', 'MEDIUM')
                ) AS "deepfakeDetectedCount",
                COUNT(*) FILTER (
                    WHERE ar.status = 'COMPLETED'
                ) AS "completedCount",
                COUNT(*) FILTER (
                    WHERE ar.status IN ('QUEUED', 'ANALYZING')
                ) AS "inProgressCount"
            FROM analysis_requests ar
            JOIN evidences e
              ON e.evidence_id = ar.evidence_id
            LEFT JOIN analysis_results r
              ON r.analysis_request_id = ar.analysis_request_id
            WHERE ar.requested_by = :uploaderId
              AND e.deleted_at IS NULL
            """, nativeQuery = true)
    DashboardStatsProjection findDashboardStatsByUploader(
            @Param("uploaderId") Long uploaderId
    );

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
              AND ar.status = com.example.demo.domain.enums.AnalysisStatus.COMPLETED
              AND ar.completedAt >= :startInclusive
              AND ar.completedAt < :endExclusive
            """)
    long countCompletedByUploaderCompletedAtBetween(
            @Param("uploaderId") Long uploaderId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
            SELECT ar
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
            ORDER BY ar.requestedAt DESC
            """)
    List<AnalysisRequest> findRecentByUploader(
            @Param("uploaderId") Long uploaderId,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE e.deletedAt IS NULL
              AND ar.requestedAt >= :startInclusive
              AND ar.requestedAt < :endExclusive
            """)
    long countRequestedBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE e.deletedAt IS NULL
              AND ar.status = com.example.demo.domain.enums.AnalysisStatus.COMPLETED
              AND ar.completedAt >= :startInclusive
              AND ar.completedAt < :endExclusive
            """)
    long countCompletedBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            JOIN AnalysisResult r ON r.analysisRequestId = ar.analysisRequestId
            WHERE e.deletedAt IS NULL
              AND ar.status = com.example.demo.domain.enums.AnalysisStatus.COMPLETED
              AND r.riskLevel IN (
                  com.example.demo.domain.enums.RiskLevel.HIGH,
                  com.example.demo.domain.enums.RiskLevel.MEDIUM
              )
              AND ar.completedAt >= :startInclusive
              AND ar.completedAt < :endExclusive
            """)
    long countDeepfakeDetectedBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
            SELECT ar
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE e.deletedAt IS NULL
              AND ar.status = com.example.demo.domain.enums.AnalysisStatus.COMPLETED
              AND ar.completedAt IS NOT NULL
              AND ar.completedAt >= :startInclusive
              AND ar.completedAt < :endExclusive
            """)
    List<AnalysisRequest> findCompletedRequestsBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    List<AnalysisRequest> findByStatusAndStartedAtBefore(AnalysisStatus status, LocalDateTime cutoff);

    List<AnalysisRequest> findByStatusAndRequestedAtBefore(AnalysisStatus status, LocalDateTime cutoff);

    long countByStatus(AnalysisStatus status);

    long countByStatusAndRequestedAtBefore(AnalysisStatus status, LocalDateTime requestedAt);

    @Query("""
            SELECT ar
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
              AND ar.status = com.example.demo.domain.enums.AnalysisStatus.COMPLETED
              AND ar.completedAt >= :startInclusive
              AND ar.completedAt < :endExclusive
            """)
    List<AnalysisRequest> findCompletedByUploaderCompletedAtBetween(
            @Param("uploaderId") Long uploaderId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );
}
