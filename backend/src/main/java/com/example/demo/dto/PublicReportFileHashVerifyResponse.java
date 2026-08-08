package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicReportFileHashVerifyResponse {

    private final String status;
    private final boolean matched;
    private final boolean storedFileIntact;
    private final String message;
    private final String reportNo;
    private final String submittedHash;
    private final String registeredHash;
}
