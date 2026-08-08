package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserStatusResponse {

    private String userId;
    private String status;
    private String processedByUserId;
}
