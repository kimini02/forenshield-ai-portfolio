package com.example.demo.repository;

import com.example.demo.domain.EvidenceMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvidenceMetadataRepository extends JpaRepository<EvidenceMetadata, Long> {

    Optional<EvidenceMetadata> findByEvidenceId(Long evidenceId);
}
