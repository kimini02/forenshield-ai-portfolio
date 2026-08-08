package com.example.demo.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminCocChainsResponse {

    private int totalCount;
    private int validCount;
    private int brokenCount;
    private List<AdminCocChainResponse> chains;
}
