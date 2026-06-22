package com.postflow.threads;

import com.postflow.auth.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the Threads authorize URL and handles the OAuth callback. The initiating user id
 * is carried in a short-lived signed {@code state} (the browser redirect can't carry our JWT).
 */
@Service
public class ThreadsOAuthService {

    private final ThreadsProperties properties;
    private final JwtService jwtService;
    private final SocialAccountService socialAccountService;

    public ThreadsOAuthService(ThreadsProperties properties,
                               JwtService jwtService,
                               SocialAccountService socialAccountService) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.socialAccountService = socialAccountService;
    }

    public String buildAuthorizeUrl(Long userId) {
        return UriComponentsBuilder.fromUriString(properties.authorizeBaseUrlOrDefault())
                .path("/oauth/authorize")
                .queryParam("client_id", properties.appId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", properties.scopesOrDefault())
                .queryParam("response_type", "code")
                .queryParam("state", jwtService.issueState(userId))
                .build()
                .toUriString();
    }

    /** Validate state, exchange the code, store the connection; returns the frontend redirect URL. */
    public String handleCallback(String code, String state) {
        Long userId = jwtService.parseUserId(state);
        socialAccountService.connectFromCode(userId, code);
        return properties.frontendRedirectUrl();
    }
}
