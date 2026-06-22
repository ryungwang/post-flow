package com.postflow.threads;

import com.postflow.threads.dto.ThreadsStatusResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/threads")
public class ThreadsController {

    private final ThreadsOAuthService oAuthService;
    private final SocialAccountService socialAccountService;

    public ThreadsController(ThreadsOAuthService oAuthService,
                             SocialAccountService socialAccountService) {
        this.oAuthService = oAuthService;
        this.socialAccountService = socialAccountService;
    }

    /** Returns the Threads authorize URL for the frontend to redirect the browser to. */
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

    @GetMapping("/status")
    public ThreadsStatusResponse status(@AuthenticationPrincipal Long userId) {
        return socialAccountService.status(userId);
    }
}
