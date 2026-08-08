package com.example.demo.dto.notification;

import com.example.demo.domain.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationDto {

    private Long notificationId;
    private NotificationType type;
    private String title;
    private String message;
    private String referenceType;
    private Long referenceId;
    private boolean read;
    private String createdAt;
}
