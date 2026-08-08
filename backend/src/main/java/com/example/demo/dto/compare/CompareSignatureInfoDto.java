package com.example.demo.dto.compare;

import com.example.demo.domain.enums.CompareSignatureStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompareSignatureInfoDto {

    private CompareSignatureStatus originalStatus;
    private CompareSignatureStatus candidateStatus;
    private String algorithm;
    private String signedBy;
    private String signedAt;
}
