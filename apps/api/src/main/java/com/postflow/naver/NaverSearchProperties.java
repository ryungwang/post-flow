package com.postflow.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 네이버 오픈API(검색) 설정 — 제휴 콘텐츠의 상품 정보 근거 채우기에 쓰는 쇼핑 검색용.
 * 네이버 개발자센터에서 발급한 Client ID/Secret(env). 미설정 시 상품 검색만 비활성(다른 기능 무관).
 */
@ConfigurationProperties(prefix = "naver")
public record NaverSearchProperties(
        String clientId,
        String clientSecret,
        String searchBaseUrl
) {
    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public String searchBaseUrlOrDefault() {
        return searchBaseUrl == null || searchBaseUrl.isBlank() ? "https://openapi.naver.com" : searchBaseUrl;
    }
}
