package com.example.demo.service.auth;

import com.example.demo.dto.signup.InviteCodeValidateResponse;
import com.example.demo.repository.InviteCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private final InviteCodeRepository inviteCodeRepository;

    @Transactional(readOnly = true)
    public InviteCodeValidateResponse validate(String code) {
        LocalDateTime now = LocalDateTime.now();
        return inviteCodeRepository.findByCode(code)
                .filter(inviteCode -> inviteCode.isUsable(now))
                .map(inviteCode -> InviteCodeValidateResponse.builder()
                        .valid(true)
                        .expiresAt(inviteCode.getExpiresAt())
                        .build())
                .orElseGet(() -> InviteCodeValidateResponse.builder()
                        .valid(false)
                        .build());
    }
}
