package com.example.demo.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 공개 보고서의 외부 URL을 한 곳에서 관리한다.
 *
 * <p>운영 URL은 {@code REPORT_PUBLIC_VERIFY_BASE_URL} 및
 * {@code REPORT_PUBLIC_VIEW_BASE_URL} 환경 변수로만 설정한다. 미발행 미리보기에는
 * 이 주소를 사용하지 않는다.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "report")
public class ReportPublicUrlProperties {

    private String publicVerifyBaseUrl;
    private String publicViewBaseUrl;
    private long publicAccessTtlDays = 7;

    public String verificationUrl(String token) {
        return appendQueryParameter(publicVerifyBaseUrl, "token", token, "REPORT_PUBLIC_VERIFY_BASE_URL");
    }

    public String publicViewUrl(String accessCode) {
        return appendQueryParameter(publicViewBaseUrl, "code", accessCode, "REPORT_PUBLIC_VIEW_BASE_URL");
    }

    private String appendQueryParameter(String baseUrl, String parameter, String value, String settingName) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException(settingName + " 환경 변수가 설정되어 있지 않습니다.");
        }
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + parameter + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
