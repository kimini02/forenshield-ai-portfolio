package com.example.demo.dto.admin;

import com.example.demo.domain.enums.OrgType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateAdminUserRequest {

    @NotBlank(message = "이름을 입력해 주세요.")
    private String displayName;

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "소속을 입력해 주세요.")
    private String department;

    private OrgType organizationType;

    /** INVESTIGATOR · REVIEWER · ORG_ADMIN (optional) */
    private String role;
}
