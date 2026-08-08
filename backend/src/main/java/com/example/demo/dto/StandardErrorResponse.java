package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StandardErrorResponse {

    private boolean success;
    private String errorCode;
    private String message;
    private List<FieldErrorDetail> details;

    @Getter
    @Builder
    public static class FieldErrorDetail {
        private String field;
        private String reason;
    }
}
