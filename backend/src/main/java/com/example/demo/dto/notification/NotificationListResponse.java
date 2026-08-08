package com.example.demo.dto.notification;

import com.example.demo.domain.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationListResponse {

    private List<NotificationDto> notifications;
    private int unreadCount;
}
