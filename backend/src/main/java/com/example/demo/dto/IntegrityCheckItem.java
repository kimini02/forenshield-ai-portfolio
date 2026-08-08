package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrityCheckItem {

    /** SIGNATURE · COC_CHAIN · BLOCKCHAIN_HASH */
    private String checkType;
    private boolean valid;
    /** 실패 시 errorCode (예: SIGNATURE_INVALID) */
    private String errorCode;
    private String message;
}
