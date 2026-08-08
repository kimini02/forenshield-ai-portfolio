package com.example.demo.dto;

import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlockchainAnchorRecordDto {

    private Long anchorId;
    private BlockchainAnchorType anchorType;
    private BlockchainAnchorStatus status;
    private String subjectHash;
    private String transactionHash;
    private Long blockNumber;
    private String network;
    private String anchoredAt;
    private Long evidenceId;
    private Long reportId;
    private String merkleBatchDate;
    private Integer merkleLeafCount;
    private String signature;
    private String signerCertHash;
    private Boolean certVerified;
    private String offchainLogHash;
    private String offchainRefJson;
    private String analysisModelJson;
    private String analysisModulesJson;
    private String errorCode;
    private String message;
    /** RQ-DTL-080 */
    private String transactionExplorerUrl;
}
