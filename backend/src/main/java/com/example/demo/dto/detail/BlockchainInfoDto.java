package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

/** RQ-DTL-078~080: 상세페이지 블록체인 등록·무결성·트랜잭션 링크 */
@Getter
@Builder
public class BlockchainInfoDto {

    private String status;
    private String anchorType;
    private String subjectHash;
    private String transactionHash;
    private String anchoredAt;
    private String network;
    /** RQ-DTL-079: 블록체인 등록 해시 vs 현재 원본 해시 일치 여부 (앵커 없으면 null) */
    private Boolean hashValid;
    /** Anchor-time certVerified snapshot (null when not applicable). */
    private Boolean certVerified;
    private String errorCode;
    private String verificationMessage;
    /** RQ-DTL-080: 익스플로러 URL (템플릿 미설정·tx 없으면 null) */
    private String transactionExplorerUrl;
}
