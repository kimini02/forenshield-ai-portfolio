package com.example.demo.repository;

import com.example.demo.domain.OverlayJob;
import com.example.demo.domain.enums.OverlayJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverlayJobRepository extends JpaRepository<OverlayJob, Long> {

    Optional<OverlayJob> findFirstByEvidenceIdAndAnalysisRequestIdAndModuleAndStatusInOrderByRequestedAtDesc(
            Long evidenceId,
            Long analysisRequestId,
            String module,
            Collection<OverlayJobStatus> statuses
    );

    List<OverlayJob> findByEvidenceIdOrderByRequestedAtDesc(Long evidenceId);
}
