package com.example.demo.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserProfileRequest {

	@NotBlank(message = "사용자 이름을 입력해 주세요.")
	private String loginId;

	@NotBlank(message = "현재 비밀번호를 입력해 주세요.")
	private String currentPassword;

	private String newPassword;
}
