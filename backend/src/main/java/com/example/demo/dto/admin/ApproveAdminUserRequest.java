package com.example.demo.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApproveAdminUserRequest {

    /** INVESTIGATOR · REVIEWER · ORG_ADMIN (ROLE_ prefix optional) */
    private String role;
}
