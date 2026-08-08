package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminDashboardStatsResponse {

    private long pendingUsers;
    private long totalUsers;
    private long todayLogs;
    private long unusedInviteCodes;
    private long cocLogs;
}
