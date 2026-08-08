package com.example.demo.dto.compare;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CompareOriginalPageResponse {

    private List<CompareFileInfoDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
