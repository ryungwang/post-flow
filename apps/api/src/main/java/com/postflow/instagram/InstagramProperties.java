package com.postflow.instagram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Instagram "API with Instagram login" 설정 — 페이스북 페이지 없이 IG 비즈니스/크리에이터 계정을
 * 직접 연결하는 경로. Facebook Login 경로({@link com.postflow.facebook.FacebookProperties})와
 * 별개 크레덴셜(Meta 앱 > Instagram > "Instagram 로그인 API 설정"의 <b>인스타그램 앱 ID</b>).
 *
 * <p>OAuth 흐름은 페북과 호스트가 다르다:
 * <ul>
 *   <li>인가 다이얼로그: {@code www.instagram.com/oauth/authorize}</li>
 *   <li>단기 토큰 교환: {@code api.instagram.com/oauth/access_token} (POST form)</li>
 *   <li>장기 토큰 교환·API 호출: {@code graph.instagram.com} (페북 graph 아님)</li>
 * </ul>
 *
 * @param appId               인스타그램 앱 ID (페북 앱 ID·Threads 앱 ID와 다름)
 * @param appSecret           인스타그램 앱 시크릿
 * @param redirectUri         OAuth redirect (앱의 "유효한 OAuth 리디렉션 URI"에 등록돼야 함)
 * @param scopes              쉼표구분 IG 로그인 권한(instagram_business_*)
 * @param authorizeBaseUrl    인가 다이얼로그 호스트 (www.instagram.com)
 * @param graphBaseUrl        Graph API 호스트 (graph.instagram.com) — IG 로그인 토큰 전용
 * @param apiBaseUrl          토큰 교환 호스트 (api.instagram.com)
 * @param apiVersion          Graph 버전 (예: v21.0)
 * @param frontendRedirectUrl 콜백 후 브라우저를 되돌릴 프론트 URL
 */
@ConfigurationProperties(prefix = "instagram")
public record InstagramProperties(
        String appId,
        String appSecret,
        String redirectUri,
        String scopes,
        String authorizeBaseUrl,
        String graphBaseUrl,
        String apiBaseUrl,
        String apiVersion,
        String frontendRedirectUrl
) {
    private static final String DEFAULT_SCOPES =
            "instagram_business_basic,instagram_business_content_publish,"
                    + "instagram_business_manage_comments,instagram_business_manage_insights";

    public String scopesOrDefault() {
        return scopes == null || scopes.isBlank() ? DEFAULT_SCOPES : scopes;
    }

    public String authorizeBaseUrlOrDefault() {
        return authorizeBaseUrl == null || authorizeBaseUrl.isBlank()
                ? "https://www.instagram.com" : authorizeBaseUrl;
    }

    /** graph.instagram.com — IG 로그인 계정의 발행·댓글·인사이트 호출 호스트(계정에 저장해 라우팅). */
    public String graphBaseUrlOrDefault() {
        return graphBaseUrl == null || graphBaseUrl.isBlank()
                ? "https://graph.instagram.com" : graphBaseUrl;
    }

    public String apiBaseUrlOrDefault() {
        return apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://api.instagram.com" : apiBaseUrl;
    }

    public String apiVersionOrDefault() {
        return apiVersion == null || apiVersion.isBlank() ? "v21.0" : apiVersion;
    }

    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
