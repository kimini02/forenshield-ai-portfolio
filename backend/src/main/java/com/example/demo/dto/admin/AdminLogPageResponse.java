package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminLogPageResponse {

    private List<AdminLogItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private List<String> departments;
}
