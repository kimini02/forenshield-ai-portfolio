package com.example.demo.dto.notification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarkAllNotificationsReadResponse {

    private int markedCount;
}
