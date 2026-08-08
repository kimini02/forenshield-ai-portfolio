package com.example.demo.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminCocChainResponse {

    private Long evidenceId;
    private String caseId;
    private String caseName;
    private int eventCount;
    private String lastEventLabel;
    private String lastEventAt;
    private String status;
    private List<AdminCocEventResponse> events;
}
