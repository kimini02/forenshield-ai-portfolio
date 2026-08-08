package com.example.demo.repository;

import com.example.demo.domain.EvidenceManifest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceManifestRepository extends JpaRepository<EvidenceManifest, Long> {
}
