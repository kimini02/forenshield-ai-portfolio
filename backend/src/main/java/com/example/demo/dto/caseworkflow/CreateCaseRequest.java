package com.example.demo.dto.caseworkflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateCaseRequest {

    @NotBlank
    @Size(max = 255)
    private String caseName;
}
