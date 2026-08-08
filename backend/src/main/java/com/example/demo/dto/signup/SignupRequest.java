package com.example.demo.dto.signup;

import com.example.demo.domain.enums.OrgType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {

    @NotBlank(message = "로그인 아이디는 필수입니다.")
    @Size(min = 4, max = 50, message = "로그인 아이디는 4~50자여야 합니다.")
    @Pattern(
            regexp = "^[A-Za-z0-9._-]+$",
            message = "로그인 아이디는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다."
    )
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$",
            message = "비밀번호는 영문 대문자, 영문 소문자, 숫자, 특수문자를 포함해야 합니다."
    )
    private String password;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String displayName;

    @NotNull(message = "기관 유형은 필수입니다.")
    private OrgType organizationType;

    @NotBlank(message = "소속 부서는 필수입니다.")
    @Size(max = 100, message = "소속 부서는 100자 이하여야 합니다.")
    private String department;

    @Size(max = 100, message = "직책/담당 업무는 100자 이하여야 합니다.")
    private String position;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    @Size(max = 20, message = "연락처는 20자 이하여야 합니다.")
    @Pattern(
            regexp = "^$|^01\\d-?\\d{3,4}-?\\d{4}$",
            message = "연락처 형식이 올바르지 않습니다."
    )
    private String phone;

    @NotBlank(message = "초대코드는 필수입니다.")
    @Size(max = 50, message = "초대코드는 50자 이하여야 합니다.")
    private String inviteCode;

    @Valid
    @NotNull(message = "약관 동의 정보는 필수입니다.")
    private Agreements agreements;

    @AssertTrue(message = "필수 약관에 동의해야 합니다.")
    public boolean isRequiredAgreementsAccepted() {
        return agreements != null
                && Boolean.TRUE.equals(agreements.terms)
                && Boolean.TRUE.equals(agreements.privacy)
                && Boolean.TRUE.equals(agreements.security);
    }

    @Getter
    @NoArgsConstructor
    public static class Agreements {
        @NotNull(message = "이용약관 동의 여부는 필수입니다.")
        private Boolean terms;

        @NotNull(message = "개인정보 처리방침 동의 여부는 필수입니다.")
        private Boolean privacy;

        @NotNull(message = "보안 정책 동의 여부는 필수입니다.")
        private Boolean security;

        private Boolean log;
    }
}
