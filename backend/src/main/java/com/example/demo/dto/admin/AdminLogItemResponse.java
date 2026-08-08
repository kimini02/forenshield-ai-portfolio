package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminLogItemResponse {

    private String id;
    private String timestamp;
    private String category;
    private String actor;
    private String actorId;
    private String actorName;
    private String department;
    private String action;
    private String detail;
}
