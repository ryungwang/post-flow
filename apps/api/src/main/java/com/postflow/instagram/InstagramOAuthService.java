package com.postflow.instagram;

import com.postflow.auth.OAuthStateService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * "Instagram API with Instagram login" 인가 URL 생성 + 콜백 처리. 페이스북 로그인 경로와 달리
 * 인가 다이얼로그가 www.instagram.com 이고 스코프가 instagram_business_* 다. 시작한 사용자 id는
 * 서명된 짧은 {@code state} 에 담아 왕복시킨다(브라우저 리다이렉트는 우리 JWT를 못 나른다).
 */
@Service
public class InstagramOAuthService {

    private final InstagramProperties props;
    private final OAuthStateService oauthStateService;
    private final InstagramConnectService connectService;

    public InstagramOAuthService(InstagramProperties props, OAuthStateService oauthStateService,
                                 InstagramConnectService connectService) {
        this.props = props;
        this.oauthStateService = oauthStateService;
        this.connectService = connectService;
    }

    public String buildAuthorizeUrl(Long userId) {
        return UriComponentsBuilder.fromUriString(props.authorizeBaseUrlOrDefault())
                .path("/oauth/authorize")
                .queryParam("client_id", props.appId())
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("scope", props.scopesOrDefault())
                .queryParam("response_type", "code")
                .queryParam("state", oauthStateService.issueState(userId))
                .build()
                .toUriString();
    }

    /** Validate state, exchange the code, connect the IG account; returns the frontend redirect URL. */
    public String handleCallback(String code, String state) {
        Long userId = oauthStateService.parseUserId(state);
        connectService.connectFromCode(userId, code);
        return props.frontendRedirectUrl();
    }
}
