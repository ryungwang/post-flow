package com.postflow.facebook;

import com.postflow.auth.OAuthStateService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the Facebook Login dialog URL and handles the OAuth callback. The initiating user id
 * is carried in a short-lived signed {@code state} (the browser redirect can't carry our JWT).
 */
@Service
public class FacebookOAuthService {

    private final FacebookProperties properties;
    private final OAuthStateService oauthStateService;
    private final FacebookConnectService connectService;

    public FacebookOAuthService(FacebookProperties properties,
                                OAuthStateService oauthStateService,
                                FacebookConnectService connectService) {
        this.properties = properties;
        this.oauthStateService = oauthStateService;
        this.connectService = connectService;
    }

    public String buildAuthorizeUrl(Long userId) {
        // appId/redirectUri가 비면 client_id 없이 FB로 보내져 "Sorry, something went wrong"만 뜬다.
        // 사용자가 원인을 알 수 있게 먼저 막는다.
        if (properties.appId() == null || properties.appId().isBlank()) {
            throw new FacebookApiException("페이스북 앱이 아직 설정되지 않았어요. (서버에 FACEBOOK_APP_ID 필요)");
        }
        if (properties.redirectUri() == null || properties.redirectUri().isBlank()) {
            throw new FacebookApiException("페이스북 redirect URI가 설정되지 않았어요. (서버에 FACEBOOK_REDIRECT_URI 필요)");
        }
        return UriComponentsBuilder.fromUriString(properties.dialogBaseUrlOrDefault())
                .path("/" + properties.apiVersionOrDefault() + "/dialog/oauth")
                .queryParam("client_id", properties.appId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", properties.scopesOrDefault())
                .queryParam("response_type", "code")
                .queryParam("state", oauthStateService.issueState(userId))
                .build()
                .toUriString();
    }

    /** Validate state, exchange the code, connect the Pages; returns the frontend redirect URL. */
    public String handleCallback(String code, String state) {
        Long userId = oauthStateService.parseUserId(state);
        connectService.connectFromCode(userId, code);
        return properties.frontendRedirectUrl();
    }
}
