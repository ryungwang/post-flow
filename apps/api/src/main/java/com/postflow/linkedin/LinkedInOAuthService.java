package com.postflow.linkedin;

import com.postflow.auth.OAuthStateService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the LinkedIn authorize URL and handles the OAuth callback. The initiating user id
 * is carried in a short-lived signed {@code state} (the browser redirect can't carry our JWT).
 */
@Service
public class LinkedInOAuthService {

    private final LinkedInProperties properties;
    private final OAuthStateService oauthStateService;
    private final LinkedInConnectService connectService;

    public LinkedInOAuthService(LinkedInProperties properties,
                                OAuthStateService oauthStateService,
                                LinkedInConnectService connectService) {
        this.properties = properties;
        this.oauthStateService = oauthStateService;
        this.connectService = connectService;
    }

    public String buildAuthorizeUrl(Long userId) {
        return UriComponentsBuilder.fromUriString(properties.authorizeBaseUrlOrDefault())
                .path("/oauth/v2/authorization")
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", properties.scopesOrDefault())
                .queryParam("state", oauthStateService.issueState(userId))
                .build()
                .toUriString();
    }

    /** Validate state, exchange the code, store the connection; returns the frontend redirect URL. */
    public String handleCallback(String code, String state) {
        Long userId = oauthStateService.parseUserId(state);
        connectService.connectFromCode(userId, code);
        return properties.frontendRedirectUrl();
    }
}
