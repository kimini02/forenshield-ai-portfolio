package com.example.demo.dto.caseworkflow;

import com.example.demo.domain.enums.EvidenceRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SetEvidenceRoleRequest {

    @NotNull
    private EvidenceRole role;
}
