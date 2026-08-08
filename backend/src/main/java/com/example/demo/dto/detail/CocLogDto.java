package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CocLogDto {

    private Long logId;
    private String eventType;
    private String userId;
    private String description;
    private String createdAt;
    private String currentLogHash;
}
