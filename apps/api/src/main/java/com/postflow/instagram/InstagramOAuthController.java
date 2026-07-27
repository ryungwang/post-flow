package com.postflow.instagram;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * "Instagram API with Instagram login" OAuth 엔드포인트 — 페이스북 페이지 없이 IG 계정을 직접
 * 연결. {@code /connect}(auth)가 인가 다이얼로그 URL을 프론트에 주고, {@code /callback}(public)이
 * 코드를 교환해 채널을 등록한 뒤 브라우저를 프론트로 되돌린다. (조회는 /social/instagram/*)
 */
@RestController
@RequestMapping("/instagram")
public class InstagramOAuthController {

    private final InstagramOAuthService oAuthService;

    public InstagramOAuthController(InstagramOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /** Returns the Instagram login dialog URL for the frontend to redirect the browser to. */
    @GetMapping("/connect")
    public Map<String, String> connect(@AuthenticationPrincipal Long userId) {
        return Map.of("authorizeUrl", oAuthService.buildAuthorizeUrl(userId));
    }

    /** OAuth redirect target (public). Exchanges the code, then bounces to the frontend. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        String redirect = oAuthService.handleCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirect))
                .build();
    }
}
