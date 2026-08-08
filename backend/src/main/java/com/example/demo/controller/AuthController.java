package com.example.demo.controller;

import com.example.demo.config.AuthRefreshProperties;
import com.example.demo.config.OpenApiConfig;
import com.example.demo.dto.AuthenticatedTokens;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.auth.StepUpExtendResponse;
import com.example.demo.dto.auth.StepUpVerifyRequest;
import com.example.demo.dto.auth.StepUpVerifyResponse;
import com.example.demo.dto.signup.SignupRequest;
import com.example.demo.dto.signup.SignupResponse;
import com.example.demo.dto.signup.UsernameCheckResponse;
import com.example.demo.security.AuthCookieSupport;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.security.ClientIpResolver;
import com.example.demo.service.auth.AuthService;
import com.example.demo.service.auth.SignupService;
import com.example.demo.service.auth.StepUpAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 인증 API — 로그인·재발급·로그아웃·회원가입.
// 리프레시 JWT는 AuthCookieSupport(HttpOnly 쿠키), 액세스 JWT는 LoginResponse 본문으로 내려준다.
@Validated
@Tag(name = "Auth", description = "로그인·회원가입 (JWT 발급)")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;
    private final AuthCookieSupport authCookieSupport;
    private final AuthRefreshProperties authRefreshProperties;
    private final StepUpAuthService stepUpAuthService;
    private final AuthUserResolver authUserResolver;
    private final ClientIpResolver clientIpResolver;

    // 액세스 JWT는 JSON, 리프레시 JWT는 Set-Cookie
    @Operation(summary = "로그인", description = "accessToken 을 응답으로 받습니다. Swagger Authorize 에 토큰만 붙여넣으세요.")
    @SecurityRequirements
    @PostMapping("/api/auth/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        AuthenticatedTokens result = authService.login(request, clientIpResolver.resolve(httpRequest));
        applyRefreshCookiePolicy(response, result.tokens().getRefreshToken());
        return ResponseEntity.ok(LoginResponse.from(result.user(), result.tokens()));
    }

    // 브라우저 쿠키의 리프레시 JWT로 액세스 JWT 재발급 (인증 불필요)
    @Operation(summary = "액세스 토큰 재발급")
    @SecurityRequirements
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (!authRefreshProperties.isEnabled()) {
            authCookieSupport.clearRefreshTokenCookie(response);
        }

        AuthenticatedTokens result = authService.reissue(refreshToken);
        applyRefreshCookiePolicy(response, result.tokens().getRefreshToken());
        return ResponseEntity.ok(LoginResponse.from(result.user(), result.tokens()));
    }

    // Redis 리프레시 삭제 + 쿠키 만료 (204)
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(authorization, refreshToken);
        authCookieSupport.clearRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    // Step-up 재인증 기능 추가
    @Operation(
            summary = "Step-up 재인증",
            description = "민감 정보(증거 상세) 조회 전 비밀번호 재확인. 성공 시 stepUpToken을 X-Step-Up-Token 헤더로 사용합니다."
    )
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/api/v1/auth/step-up/verify")
    public ResponseEntity<StepUpVerifyResponse> verifyStepUp(@Valid @RequestBody StepUpVerifyRequest request) {
        StepUpVerifyResponse response = stepUpAuthService.verifyAndIssueToken(
                authUserResolver.requireCurrentUser(),
                request.getPassword()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Step-up 세션 연장",
            description = "유효한 X-Step-Up-Token으로 남은 시간이 5분 이하일 때 15분을 추가 연장합니다."
    )
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/api/v1/auth/step-up/extend")
    public ResponseEntity<StepUpExtendResponse> extendStepUp(
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        StepUpExtendResponse response = stepUpAuthService.extendToken(
                authUserResolver.requireCurrentUser(),
                stepUpToken
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원가입")
    @SecurityRequirements
    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signupService.signup(request));
    }

    @GetMapping("/api/v1/auth/username/check")
    public UsernameCheckResponse checkUsername(
            @RequestParam @NotBlank(message = "로그인 아이디는 필수입니다.") String loginId
    ) {
        return UsernameCheckResponse.builder()
                .available(signupService.isLoginIdAvailable(loginId))
                .build();
    }

    private void applyRefreshCookiePolicy(HttpServletResponse response, String refreshToken) {
        if (authRefreshProperties.isEnabled()) {
            authCookieSupport.addRefreshTokenCookie(response, refreshToken);
            return;
        }
        authCookieSupport.clearRefreshTokenCookie(response);
    }
}
