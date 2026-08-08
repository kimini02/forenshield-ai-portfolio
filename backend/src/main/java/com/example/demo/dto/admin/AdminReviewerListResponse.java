package com.example.demo.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminReviewerListResponse {

    private List<AdminReviewerItemResponse> reviewers;
}
