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
