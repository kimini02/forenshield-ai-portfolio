package com.example.demo.dto.evidence;

import com.example.demo.domain.enums.EvidenceAccessEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EvidenceAccessEventRequest {

    @NotNull
    private EvidenceAccessEventType eventType;

    @Size(max = 100)
    private String caseKey;

    @Size(max = 100)
    private String source;
}
