package com.example.demo.repository;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.enums.BlockchainAnchorType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BlockchainAnchorRepository extends JpaRepository<BlockchainAnchor, Long> {

    Optional<BlockchainAnchor> findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
            Long evidenceId,
            BlockchainAnchorType anchorType
    );

    List<BlockchainAnchor> findByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
            Long evidenceId,
            BlockchainAnchorType anchorType
    );

    Optional<BlockchainAnchor> findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
            Long reportId,
            BlockchainAnchorType anchorType
    );

    boolean existsByMerkleBatchDateAndAnchorType(LocalDate merkleBatchDate, BlockchainAnchorType anchorType);

    Optional<BlockchainAnchor> findTopByMerkleBatchDateAndAnchorTypeOrderByCreatedAtDesc(
            LocalDate merkleBatchDate,
            BlockchainAnchorType anchorType
    );
}
