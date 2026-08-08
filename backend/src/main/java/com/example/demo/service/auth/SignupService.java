package com.example.demo.service.auth;

import com.example.demo.domain.InviteCode;
import com.example.demo.domain.User;
import com.example.demo.dto.signup.SignupRequest;
import com.example.demo.dto.signup.SignupResponse;
import com.example.demo.exception.DuplicateSignupFieldException;
import com.example.demo.exception.InvalidInviteCodeException;
import com.example.demo.repository.InviteCodeRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SignupService {

    private static final String SIGNUP_ACCEPTED_MESSAGE = "가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.";

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateDuplicateFields(request);

        LocalDateTime now = LocalDateTime.now();
        InviteCode inviteCode = inviteCodeRepository.findByCode(request.getInviteCode())
                .filter(code -> code.isUsable(now))
                .orElseThrow(() -> new InvalidInviteCodeException("유효하지 않은 초대코드입니다."));

        User user = User.builder()
                .loginId(request.getLoginId())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getDisplayName())
                .phone(request.getPhone())
                .organizationType(request.getOrganizationType())
                .department(request.getDepartment())
                .position(request.getPosition())
                .inviteCode(inviteCode)
                .build();

        User savedUser = userRepository.save(user);
        inviteCode.markUsedBy(savedUser.getUserId(), now);

        return SignupResponse.builder()
                .userId(String.valueOf(savedUser.getUserId()))
                .status(savedUser.getStatus())
                .message(SIGNUP_ACCEPTED_MESSAGE)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("로그인 아이디는 필수입니다.");
        }
        return !userRepository.existsByLoginId(loginId);
    }

    private void validateDuplicateFields(SignupRequest request) {
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new DuplicateSignupFieldException("loginId", "이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateSignupFieldException("email", "이미 사용 중인 이메일입니다.");
        }
    }
}
