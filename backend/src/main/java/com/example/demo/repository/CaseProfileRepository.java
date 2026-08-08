package com.example.demo.repository;

import com.example.demo.domain.CaseProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseProfileRepository extends JpaRepository<CaseProfile, Long> {

    Optional<CaseProfile> findByUploaderIdAndCaseKey(Long uploaderId, String caseKey);

    List<CaseProfile> findByUploaderId(Long uploaderId);

    List<CaseProfile> findByUploaderIdIn(Collection<Long> uploaderIds);

    List<CaseProfile> findByReviewerId(Long reviewerId);

    List<CaseProfile> findByCaseKey(String caseKey);

    List<CaseProfile> findByUploaderIdAndCaseKeyIn(Long uploaderId, Collection<String> caseKeys);

    boolean existsByUploaderIdAndCaseKey(Long uploaderId, String caseKey);

    @Query("""
            SELECT cp
            FROM CaseProfile cp
            WHERE cp.caseKey = :caseKey
              AND cp.uploaderId IN :uploaderIds
            """)
    List<CaseProfile> findByCaseKeyAndUploaderIdIn(
            @Param("caseKey") String caseKey,
            @Param("uploaderIds") Collection<Long> uploaderIds
    );

    Optional<CaseProfile> findByReviewerIdAndCaseKey(Long reviewerId, String caseKey);
}
