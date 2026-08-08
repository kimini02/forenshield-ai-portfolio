package com.example.demo.dto.caseworkflow;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AssignCaseReviewerRequest {

    @NotBlank(message = "reviewerId is required")
    private String reviewerId;

    /** Owner uploader id when org admin assigns across investigators. */
    private String uploaderId;
}
