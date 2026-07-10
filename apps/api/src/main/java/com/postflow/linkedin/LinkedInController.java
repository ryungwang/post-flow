package com.postflow.linkedin;

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
 * LinkedIn OAuth endpoints. {@code /connect} (auth) hands the authorize URL to the frontend;
 * {@code /callback} (public) is the OAuth redirect target that exchanges the code and bounces
 * the browser back to the app. Channel list / set-default / disconnect are on /social/*.
 */
@RestController
@RequestMapping("/linkedin")
public class LinkedInController {

    private final LinkedInOAuthService oAuthService;

    public LinkedInController(LinkedInOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /** Returns the LinkedIn authorize URL for the frontend to redirect the browser to. */
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
