package com.example.demo.config;

// RefreshTokenRedisService 는 Redis 에 접근할 때 StringRedisTemplate를 필요로한다. 

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// application-*.yaml의 spring.data.redis 설정으로 연결 팩토리가 있을 때만 활성화된다.
// StringRedisTemplate 빈을 등록하고, RefreshTokenRedisService가 Redis에 접근할 때 사용한다.
@Configuration
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisConfig {

    // Spring Boot 자동 등록이 없을 때만 생성 (운영: ElastiCache TLS, 로컬: localhost:6379)
    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
