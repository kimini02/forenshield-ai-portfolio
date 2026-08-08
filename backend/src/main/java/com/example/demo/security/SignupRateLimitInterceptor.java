package com.example.demo.security;

import com.example.demo.dto.StandardErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SignupRateLimitInterceptor implements HandlerInterceptor {

    private final SignupRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        RateLimitDecision decision = rateLimitService.check(
                request.getMethod(),
                request.getRequestURI(),
                clientIpResolver.resolve(request),
                resolveAuthenticatedLoginId()
        );

        if (decision.allowed()) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), StandardErrorResponse.builder()
                .success(false)
                .errorCode("RATE_LIMIT_EXCEEDED")
                .message("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.")
                .build());
        return false;
    }

    private String resolveAuthenticatedLoginId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal == null || "anonymousUser".equals(principal)) {
            return null;
        }
        return principal.toString();
    }
}
