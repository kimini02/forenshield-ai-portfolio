package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEvidenceCustodyLogResponse {

    private String id;
    private String timestamp;
    private String category;
    private String actor;
    private String action;
    private String detail;
}
