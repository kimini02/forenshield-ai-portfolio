package com.example.demo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class RestSecurityErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestAuthenticationEntryPoint authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
    private final RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler(objectMapper);

    @Test
    void authenticationEntryPoint_returnsStandardUnauthorizedJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("bad credentials")
        );

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).contains("application/json");
        assertThat(objectMapper.readTree(response.getContentAsString()).get("success").asBoolean()).isFalse();
        assertThat(objectMapper.readTree(response.getContentAsString()).get("errorCode").asText())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void accessDeniedHandler_returnsStandardForbiddenJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
                new MockHttpServletRequest(),
                response,
                new org.springframework.security.access.AccessDeniedException("denied")
        );

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(objectMapper.readTree(response.getContentAsString()).get("errorCode").asText())
                .isEqualTo("FORBIDDEN");
        assertThat(objectMapper.readTree(response.getContentAsString()).get("message").asText())
                .isEqualTo("이 요청을 수행할 권한이 없습니다.");
    }
}
