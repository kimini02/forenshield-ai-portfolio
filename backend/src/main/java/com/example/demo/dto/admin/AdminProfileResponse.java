package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminProfileResponse {

    private String username;
    private String displayName;
    private String email;
    private String department;
    private String phone;
    private String role;
}
