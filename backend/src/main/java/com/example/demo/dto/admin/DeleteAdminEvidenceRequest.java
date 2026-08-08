package com.example.demo.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeleteAdminEvidenceRequest {

    @NotBlank(message = "삭제 사유를 입력해 주세요.")
    private String reason;
}
