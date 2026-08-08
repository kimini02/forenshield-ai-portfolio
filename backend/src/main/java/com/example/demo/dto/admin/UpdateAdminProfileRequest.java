package com.example.demo.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateAdminProfileRequest {

    @NotBlank(message = "아이디를 입력해 주세요.")
    @Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 4~20자의 영문 소문자, 숫자, 밑줄(_)만 사용할 수 있습니다.")
    private String username;

    @NotBlank(message = "이름을 입력해 주세요.")
    private String displayName;

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "소속을 입력해 주세요.")
    private String department;

    private String phone;
}
