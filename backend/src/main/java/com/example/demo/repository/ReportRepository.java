package com.example.demo.repository;

import com.example.demo.domain.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findTopByEvidenceIdOrderByCreatedAtDesc(Long evidenceId);

    Optional<Report> findTopByEvidenceIdAndCompareIdIsNullOrderByCreatedAtDesc(Long evidenceId);

    Optional<Report> findTopByAnalysisResultIdOrderByCreatedAtDesc(Long analysisResultId);

    List<Report> findByEvidenceIdOrderByCreatedAtDesc(Long evidenceId);

    List<Report> findByEvidenceIdAndCompareIdIsNullOrderByCreatedAtDesc(Long evidenceId);

    long countByEvidenceIdAndCompareIdIsNull(Long evidenceId);

    Optional<Report> findTopByCompareIdOrderByCreatedAtDesc(Long compareId);

    Optional<Report> findByEvidenceIdAndReportHash(Long evidenceId, String reportHash);

    Optional<Report> findByVerificationToken(String verificationToken);

    Optional<Report> findByVerificationCode(String verificationCode);

    Optional<Report> findByPublicAccessCode(String publicAccessCode);

    Page<Report> findByCreatedByOrderByCreatedAtDesc(Long createdBy, Pageable pageable);

    @Query("""
            SELECT r FROM Report r
            WHERE r.publicationStatus = com.example.demo.domain.enums.ReportPublicationStatus.ISSUED
              AND r.evidenceId IN (
                SELECT e.evidenceId
                FROM Evidence e
                JOIN CaseProfile cp ON cp.uploaderId = e.uploaderId
                  AND cp.caseKey = COALESCE(NULLIF(e.caseNumber, ''), e.caseName, CONCAT('EVIDENCE-', e.evidenceId))
                WHERE e.deletedAt IS NULL
                  AND cp.reviewerId = :reviewerId
              )
            """)
    Page<Report> findIssuedByReviewerAssignment(
            @Param("reviewerId") Long reviewerId,
            Pageable pageable
    );
}
