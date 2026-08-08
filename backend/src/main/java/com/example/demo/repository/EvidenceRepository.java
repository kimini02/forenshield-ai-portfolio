package com.example.demo.repository;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.EvidenceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence, Long>, JpaSpecificationExecutor<Evidence> {

    List<Evidence> findByUploaderIdAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
            Long uploaderId,
            EvidenceStatus status
    );

    List<Evidence> findByStatusAndDeletedAtIsNullOrderByUploadedAtDesc(EvidenceStatus status);

    @Query("""
            SELECT e
            FROM Evidence e
            WHERE e.deletedAt IS NULL
              AND e.status = :status
              AND e.uploaderId IN :uploaderIds
            ORDER BY e.uploadedAt DESC
            """)
    List<Evidence> findByUploaderIdInAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
            @Param("uploaderIds") List<Long> uploaderIds,
            @Param("status") EvidenceStatus status
    );

    @Query("""
            SELECT e
            FROM Evidence e
            JOIN CaseProfile cp ON cp.uploaderId = e.uploaderId
              AND cp.caseKey = COALESCE(NULLIF(e.caseNumber, ''), e.caseName, CONCAT('EVIDENCE-', e.evidenceId))
            WHERE e.deletedAt IS NULL
              AND e.status = :status
              AND cp.reviewerId = :reviewerId
            ORDER BY e.uploadedAt DESC
            """)
    List<Evidence> findByReviewerAssignmentAndStatus(
            @Param("reviewerId") Long reviewerId,
            @Param("status") EvidenceStatus status
    );

    Optional<Evidence> findByEvidenceId(Long evidenceId);

    Optional<Evidence> findByEvidenceIdAndDeletedAtIsNull(Long evidenceId);

    List<Evidence> findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(
            List<Long> evidenceIds,
            Long uploaderId
    );

    List<Evidence> findByEvidenceIdInAndDeletedAtIsNull(List<Long> evidenceIds);

    Optional<Evidence> findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(
            Long evidenceId,
            Long uploaderId
    );

    @Query("""
            SELECT e
            FROM Evidence e
            WHERE e.uploaderId = :uploaderId
              AND e.deletedAt IS NULL
              AND (e.caseNumber = :caseKey OR e.caseName = :caseKey)
            ORDER BY e.uploadedAt DESC
            """)
    List<Evidence> findByUploaderIdAndCaseKey(
            @Param("uploaderId") Long uploaderId,
            @Param("caseKey") String caseKey
    );

    @Query("""
            SELECT e
            FROM Evidence e
            WHERE e.deletedAt IS NULL
              AND (e.caseNumber = :caseKey OR e.caseName = :caseKey)
            ORDER BY e.uploadedAt DESC
            """)
    List<Evidence> findByCaseKey(@Param("caseKey") String caseKey);

    @Query("""
            SELECT e
            FROM Evidence e
            WHERE e.deletedAt IS NULL
              AND (e.caseNumber = :caseKey OR e.caseName = :caseKey)
              AND e.uploaderId IN :uploaderIds
            ORDER BY e.uploadedAt DESC
            """)
    List<Evidence> findByCaseKeyAndUploaderIdIn(
            @Param("caseKey") String caseKey,
            @Param("uploaderIds") List<Long> uploaderIds
    );

    @Query("""
            SELECT e
            FROM Evidence e
            WHERE e.uploaderId = :uploaderId
              AND e.deletedAt IS NULL
              AND (
                COALESCE(:search, '') = '' OR
                LOWER(e.fileName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(COALESCE(e.caseName, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(COALESCE(e.caseNumber, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY e.uploadedAt DESC
            """)
    Page<Evidence> findCompareOriginals(
            @Param("uploaderId") Long uploaderId,
            @Param("search") String search,
            Pageable pageable
    );
}
