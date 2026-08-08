package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ForenShield API")
                        .description("""
                                로컬 Swagger 테스트 순서:
                                1) Auth > POST /api/auth/login (1111 / 2222)
                                2) 우측 상단 Authorize > accessToken 붙여넣기 (Bearer 없이)
                                3) Evidence > upload → readiness → analyze
                                """)
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .name(BEARER_AUTH)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("LoginResponse.accessToken (Bearer 접두사 없이)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
