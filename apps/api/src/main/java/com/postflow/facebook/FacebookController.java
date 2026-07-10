package com.postflow.facebook;

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
 * Facebook Pages OAuth endpoints. {@code /connect} (auth) hands the login-dialog URL to the
 * frontend; {@code /callback} (public) exchanges the code, connects the user's Pages, and
 * bounces the browser back. Channel list / set-default / disconnect are on /social/*.
 */
@RestController
@RequestMapping("/facebook")
public class FacebookController {

    private final FacebookOAuthService oAuthService;

    public FacebookController(FacebookOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /** Returns the Facebook login dialog URL for the frontend to redirect the browser to. */
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
