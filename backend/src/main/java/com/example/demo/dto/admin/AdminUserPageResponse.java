package com.example.demo.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminUserPageResponse {

    private List<AdminUserItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
