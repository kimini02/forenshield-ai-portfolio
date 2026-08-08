package com.example.demo.service.admin;

import com.example.demo.domain.CustodyLog;
import com.example.demo.dto.admin.AdminDashboardStatsResponse;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.InviteCodeRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final CustodyLogRepository custodyLogRepository;

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime now = LocalDateTime.now();

        return AdminDashboardStatsResponse.builder()
                .pendingUsers(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.PENDING))
                .totalUsers(userRepository.countByDeletedAtIsNull())
                .todayLogs(custodyLogRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        todayStart,
                        tomorrowStart
                ))
                .unusedInviteCodes(inviteCodeRepository.countUnused(now))
                .cocLogs(custodyLogRepository.countByActionTypeIn(LogCategoryMapper.cocActionTypes()))
                .build();
    }
}
