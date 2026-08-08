package com.example.demo.config;

import com.example.demo.security.SignupRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SignupRateLimitInterceptor signupRateLimitInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(signupRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/signup",
                        "/api/v1/auth/username/check",
                        "/api/v1/invite-codes/validate",
                        "/api/v1/auth/step-up/verify",
                        "/api/v1/auth/step-up/extend"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(parseAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Report-Hash", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private String[] parseAllowedOrigins() {
        return allowedOrigins.split("\\s*,\\s*");
    }
}
