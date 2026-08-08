package com.example.demo.dto.compare;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisteredCompareRequest {

    @NotNull
    private Long originalEvidenceId;

    @NotNull
    private Long candidateEvidenceId;
}
