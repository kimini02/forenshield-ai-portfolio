package com.example.demo.util;

import com.example.demo.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseKeyNormalizerTest {

    @Test
    void requireCaseKey_rejectsBlank() {
        assertThatThrownBy(() -> CaseKeyNormalizer.requireCaseKey("  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(businessException.getErrorCode()).isEqualTo("INVALID_REQUEST");
                    assertThat(businessException.getMessage()).isEqualTo("사건 식별자가 필요합니다.");
                });
    }

    @Test
    void resolveCaseKey_prefersQueryParamOverPath() {
        assertThat(CaseKeyNormalizer.resolveCaseKey("query-key", "path-key")).isEqualTo("query-key");
    }

    @Test
    void resolveCaseKey_fallsBackToPathWhenQueryMissing() {
        assertThat(CaseKeyNormalizer.resolveCaseKey(null, "path-key")).isEqualTo("path-key");
    }
}
