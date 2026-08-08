package com.example.demo.config;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 H2 개발 DB에 테스트 로그인 계정을 보장합니다.
 * 프론트 mock-auth와 동일: 1111/2222(USER), 3333/4444(ADMIN)
 */
@Component
@Profile({"local", "default", "test"})
@RequiredArgsConstructor
public class LocalDevUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!isH2Database()) {
            return;
        }

        ensureDevUser("1111", "2222", "테스트 사용자", UserRole.ROLE_USER);
        ensureDevUser("3333", "4444", "테스트 관리자", UserRole.ROLE_ADMIN);
    }

    private void ensureDevUser(String loginId, String rawPassword, String name, UserRole role) {
        userRepository.findByLoginIdAndDeletedAtIsNull(loginId)
                .ifPresentOrElse(
                        user -> user.syncDevCredentials(role, passwordEncoder.encode(rawPassword)),
                        () -> userRepository.save(createUser(loginId, rawPassword, name, role))
                );
    }

    private boolean isH2Database() {
        String url = environment.getProperty("spring.datasource.url", "");
        return url.startsWith("jdbc:h2:");
    }

    private User createUser(String loginId, String rawPassword, String name, UserRole role) {
        return User.builder()
                .loginId(loginId)
                .email(loginId + "@local.dev")
                .password(passwordEncoder.encode(rawPassword))
                .name(name)
                .organizationType(OrgType.ETC)
                .department("로컬개발팀")
                .role(role)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build();
    }
}
