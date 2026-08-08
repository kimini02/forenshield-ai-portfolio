package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminReviewerItemResponse {

    private String id;
    private String name;
    private String department;
    private String organizationId;
    private String organizationName;
    private String organizationType;
}
