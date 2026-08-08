package com.example.demo.dto.compare;

import com.example.demo.domain.enums.CompareBlockchainStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompareBlockchainInfoDto {

    private CompareBlockchainStatus status;
    private String network;
    private String txHash;
    private Long blockNumber;
    private String anchoredAt;
    private String anchoredHash;
}
