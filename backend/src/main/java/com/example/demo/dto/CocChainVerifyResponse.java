package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CocChainVerifyResponse {

    private Long evidenceId;
    private boolean valid;
    private int logCount;
    private Long brokenAtLogId;
    private String failureReason;
    private String message;
}
