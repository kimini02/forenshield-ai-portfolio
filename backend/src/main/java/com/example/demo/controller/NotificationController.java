package com.example.demo.controller;

import com.example.demo.dto.notification.MarkAllNotificationsReadResponse;
import com.example.demo.dto.notification.NotificationDto;
import com.example.demo.dto.notification.NotificationListResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "알림 목록 조회", description = "RQ-COM-015: 최근 알림 목록")
    @GetMapping
    public NotificationListResponse listNotifications(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return notificationService.listNotifications(
                authUserResolver.requireCurrentUser().getUserId(),
                limit
        );
    }

    @Operation(summary = "알림 읽음 처리", description = "RQ-COM-016: 알림 확인")
    @PatchMapping("/{notificationId}/read")
    public NotificationDto markAsRead(@PathVariable Long notificationId) {
        return notificationService.markAsRead(
                authUserResolver.requireCurrentUser().getUserId(),
                notificationId
        );
    }

    @Operation(summary = "알림 전체 읽음 처리", description = "RQ-COM-015: 헤더 알림 일괄 확인")
    @PatchMapping("/read-all")
    public MarkAllNotificationsReadResponse markAllAsRead() {
        int markedCount = notificationService.markAllAsRead(
                authUserResolver.requireCurrentUser().getUserId()
        );
        return MarkAllNotificationsReadResponse.builder()
                .markedCount(markedCount)
                .build();
    }
}
