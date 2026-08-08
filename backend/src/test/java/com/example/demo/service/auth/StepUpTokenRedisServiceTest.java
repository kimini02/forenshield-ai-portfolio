package com.example.demo.service.auth;

import com.example.demo.config.JwtProperties;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepUpTokenRedisServiceTest {

    private StepUpTokenRedisService service;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = mock(JwtProperties.class);
        when(jwtProperties.resolveStepUpExpirationMinutes()).thenReturn(15L);
        service = new StepUpTokenRedisService(null, jwtProperties);
    }

    @Test
    @DisplayName("남은 시간 4분일 때 15분 연장하면 약 19분 남은 ms를 반환한다")
    void extendToken_withinWindow_addsExtension() throws Exception {
        String token = service.issueToken(1L);
        setMemoryExpiry(token, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(4));

        Long remainingBefore = service.resolveRemainingMs(token);
        assertThat(remainingBefore).isNotNull();
        assertThat(remainingBefore).isLessThanOrEqualTo(TimeUnit.MINUTES.toMillis(4) + 1000);

        Long newRemainingMs = service.extendToken(token, TimeUnit.MINUTES.toMillis(15));
        assertThat(newRemainingMs).isNotNull();
        assertThat(newRemainingMs).isGreaterThan(TimeUnit.MINUTES.toMillis(18));
        assertThat(newRemainingMs).isLessThanOrEqualTo(TimeUnit.MINUTES.toMillis(19) + 1000);
    }

    @Test
    @DisplayName("만료된 토큰은 연장할 수 없다")
    void extendToken_expired_returnsNull() throws Exception {
        String token = service.issueToken(1L);
        setMemoryExpiry(token, System.currentTimeMillis() - 1000);

        assertThat(service.extendToken(token, TimeUnit.MINUTES.toMillis(15))).isNull();
        assertThat(service.resolveUserId(token)).isNull();
    }

    @SuppressWarnings("unchecked")
    private void setMemoryExpiry(String token, long expiresAtEpochMs) throws Exception {
        Field memoryStoreField = StepUpTokenRedisService.class.getDeclaredField("memoryStore");
        memoryStoreField.setAccessible(true);
        Map<String, Object> memoryStore = (Map<String, Object>) memoryStoreField.get(service);
        String redisKey = "STEPUP:" + token;
        Object entry = memoryStore.get(redisKey);
        assertThat(entry).isNotNull();

        Class<?> entryClass = entry.getClass();
        long userId = (long) entryClass.getMethod("userId").invoke(entry);
        var constructor = entryClass.getDeclaredConstructor(long.class, long.class);
        constructor.setAccessible(true);
        memoryStore.put(redisKey, constructor.newInstance(userId, expiresAtEpochMs));
    }
}
