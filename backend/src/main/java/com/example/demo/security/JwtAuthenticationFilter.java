package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.example.demo.util.UserRoleSupport;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 요청마다 Authorization Bearer 헤더의 액세스 JWT를 검증하고 SecurityContext에 로그인 정보를 넣는다.
// JwtTokenProvider로 파싱하고, AuthUserResolver·@PreAuthorize가 이후 사용자 조회에 사용한다.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            jwtTokenProvider.parseToken(token).ifPresent(claims -> {
                // 리프레시 JWT가 Bearer로 오면 인증하지 않음
                if (!jwtTokenProvider.isAccessToken(claims)) {
                    return;
                }
                String loginId = claims.get("loginId", String.class);
                if (loginId == null || loginId.isBlank()) {
                    loginId = claims.getSubject();
                }
                String role = claims.get("role", String.class);
                var authorities = UserRoleSupport.toAuthorities(role);

                var authentication = new UsernamePasswordAuthenticationToken(loginId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
