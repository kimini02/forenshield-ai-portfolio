package com.example.demo.dto.caseworkflow;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SetRepresentativeEvidenceRequest {

    @NotNull
    private Long evidenceId;
}
