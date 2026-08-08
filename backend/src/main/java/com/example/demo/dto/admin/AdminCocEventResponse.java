package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminCocEventResponse {

    private Long logId;
    private String eventType;
    private String label;
    private String actor;
    private String createdAt;
    private String currentLogHash;
    private boolean chainValid;
    private String detail;
}
