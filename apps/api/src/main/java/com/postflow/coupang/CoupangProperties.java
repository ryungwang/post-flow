package com.postflow.coupang;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 쿠팡 파트너스 Open API 설정. ACCESS/SECRET 키는 파트너스 콘솔 → API 관리에서 발급.
 * 크레덴셜은 이미지에 넣지 않고 운영 {@code .env}(COUPANG_ACCESS_KEY/COUPANG_SECRET_KEY)로 주입.
 */
@ConfigurationProperties(prefix = "coupang")
public record CoupangProperties(
        String accessKey,
        String secretKey,
        String baseUrl
) {
    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api-gateway.coupang.com" : baseUrl;
    }

    public boolean configured() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
