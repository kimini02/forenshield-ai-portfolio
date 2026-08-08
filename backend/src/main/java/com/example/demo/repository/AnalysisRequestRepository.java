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

    List<AnalysisRequest> findByEvidenceIdOrderByRequestedAtDesc(Long evidenceId);

    boolean existsByEvidenceId(Long evidenceId);

    boolean existsByEvidenceIdAndStatus(Long evidenceId, AnalysisStatus status);

    boolean existsByEvidenceIdAndStatusIn(Long evidenceId, List<AnalysisStatus> statuses);

    Optional<AnalysisRequest> findTopByEvidenceIdOrderByRequestedAtDesc(Long evidenceId);

    void deleteByEvidenceId(Long evidenceId);

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
            """)
    long countTotalByUploader(@Param("uploaderId") Long uploaderId);

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
              AND ar.status = :status
            """)
    long countByUploaderAndStatus(
            @Param("uploaderId") Long uploaderId,
            @Param("status") AnalysisStatus status
    );

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
              AND ar.status IN :statuses
            """)
    long countByUploaderAndStatusIn(
            @Param("uploaderId") Long uploaderId,
            @Param("statuses") List<AnalysisStatus> statuses
    );

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            JOIN AnalysisResult r ON r.analysisRequestId = ar.analysisRequestId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
              AND ar.status = com.example.demo.domain.enums.AnalysisStatus.COMPLETED
              AND r.riskLevel IN (
                  com.example.demo.domain.enums.RiskLevel.HIGH,
                  com.example.demo.domain.enums.RiskLevel.MEDIUM
              )
            """)
    long countDeepfakeDetectedByUploader(@Param("uploaderId") Long uploaderId);

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
