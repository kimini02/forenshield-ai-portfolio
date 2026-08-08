package com.example.demo.dto.caseworkflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExcludeEvidenceRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
