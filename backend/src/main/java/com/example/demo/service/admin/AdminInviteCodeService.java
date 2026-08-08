package com.example.demo.service.admin;

import com.example.demo.domain.InviteCode;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.InviteStatus;
import com.example.demo.dto.admin.AdminInviteCodeResponse;
import com.example.demo.dto.admin.CreateInviteCodeRequest;
import com.example.demo.exception.AdminException;
import com.example.demo.repository.InviteCodeRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminInviteCodeService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int DEFAULT_EXPIRES_IN_DAYS = 30;

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<AdminInviteCodeResponse> listInviteCodes() {
        LocalDateTime now = LocalDateTime.now();
        List<InviteCode> codes = inviteCodeRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> loginIdsByUserId = resolveUsedByLoginIds(codes);

        return codes.stream()
                .map(code -> toResponse(code, now, loginIdsByUserId))
                .toList();
    }

    @Transactional
    public AdminInviteCodeResponse createInviteCode(User admin, CreateInviteCodeRequest request) {
        int expiresInDays = request.getExpiresInDays() == null || request.getExpiresInDays() <= 0
                ? DEFAULT_EXPIRES_IN_DAYS
                : request.getExpiresInDays();

        LocalDateTime now = LocalDateTime.now();
        InviteCode inviteCode = InviteCode.builder()
                .code(generateUniqueCode())
                .organizationType(admin.getOrganizationType())
                .issuedBy(admin.getUserId())
                .status(InviteStatus.ACTIVE)
                .expiresAt(now.plusDays(expiresInDays))
                .build();

        InviteCode saved = inviteCodeRepository.save(inviteCode);
        return toResponse(saved, now, Map.of());
    }

    private Map<Long, String> resolveUsedByLoginIds(List<InviteCode> codes) {
        List<Long> usedByIds = codes.stream()
                .map(InviteCode::getUsedBy)
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (usedByIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(usedByIds).stream()
                .collect(Collectors.toMap(User::getUserId, User::getLoginId));
    }

    private AdminInviteCodeResponse toResponse(
            InviteCode code,
            LocalDateTime now,
            Map<Long, String> loginIdsByUserId
    ) {
        String usedBy = code.getUsedBy() == null ? null : loginIdsByUserId.get(code.getUsedBy());

        return AdminInviteCodeResponse.builder()
                .id(String.valueOf(code.getInviteCodeId()))
                .code(code.getCode())
                .createdAt(code.getCreatedAt().toLocalDate().toString())
                .expiresAt(code.getExpiresAt() == null ? null : code.getExpiresAt().toLocalDate().toString())
                .status(resolveDisplayStatus(code, now))
                .usedBy(usedBy)
                .build();
    }

    private String resolveDisplayStatus(InviteCode code, LocalDateTime now) {
        if (code.getStatus() == InviteStatus.USED) {
            return "USED";
        }
        if (code.getStatus() == InviteStatus.EXPIRED
                || code.getStatus() == InviteStatus.REVOKED
                || (code.getExpiresAt() != null && !code.getExpiresAt().isAfter(now))) {
            return "EXPIRED";
        }
        return "UNUSED";
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = "VF-" + randomPart() + "-" + randomPart();
            if (!inviteCodeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new AdminException(HttpStatus.INTERNAL_SERVER_ERROR, "초대코드 생성에 실패했습니다.");
    }

    private String randomPart() {
        StringBuilder builder = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            builder.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
        }
        return builder.toString();
    }
}
