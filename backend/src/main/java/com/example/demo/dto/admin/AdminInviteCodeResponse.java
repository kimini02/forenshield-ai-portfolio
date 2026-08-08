package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminInviteCodeResponse {

    private String id;
    private String code;
    private String createdAt;
    private String expiresAt;
    private String status;
    private String usedBy;
}
