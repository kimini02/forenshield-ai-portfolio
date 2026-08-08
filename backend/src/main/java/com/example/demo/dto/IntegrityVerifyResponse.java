package com.example.demo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** RQ-SEC-153 / SK-632: 증거 무결성·서명·블록체인 검증 결과 */
@Getter
@Builder
public class IntegrityVerifyResponse {

    private Long evidenceId;
    private boolean valid;
    private List<IntegrityCheckItem> checks;
}
