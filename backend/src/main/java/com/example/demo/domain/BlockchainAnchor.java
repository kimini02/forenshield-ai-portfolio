package com.example.demo.domain;

import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "blockchain_anchors")
@Getter
@Setter
@NoArgsConstructor
public class BlockchainAnchor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "anchor_id")
    private Long anchorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "anchor_type", nullable = false, length = 30)
    private BlockchainAnchorType anchorType;

    @Column(name = "subject_hash", nullable = false, length = 64)
    private String subjectHash;

    @Column(name = "evidence_id")
    private Long evidenceId;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "merkle_batch_date")
    private LocalDate merkleBatchDate;

    @Column(name = "merkle_leaf_count")
    private Integer merkleLeafCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockchainAnchorStatus status;

    @Column(name = "transaction_hash", length = 128)
    private String transactionHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(length = 50)
    private String network;

    @Column(name = "anchored_at")
    private LocalDateTime anchoredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** Ledger snapshot: manifest signature value at anchor time. */
    @Column(name = "signature_value", columnDefinition = "text")
    private String signatureValue;

    @Column(name = "signer_certificate_hash", length = 64)
    private String signerCertificateHash;

    @Column(name = "cert_verified")
    private Boolean certVerified;

    @Column(name = "offchain_log_hash", length = 64)
    private String offchainLogHash;

    @Column(name = "offchain_ref_json", columnDefinition = "text")
    private String offchainRefJson;

    @Column(name = "analysis_model_json", columnDefinition = "text")
    private String analysisModelJson;

    @Column(name = "analysis_modules_json", columnDefinition = "text")
    private String analysisModulesJson;
}
