package com.postflow.auth;

import com.postflow.auth.dto.UserDto;
import com.postflow.billing.EntitlementService;
import com.postflow.user.User;
import com.postflow.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Auth is centralized in synub-sso. This controller proxies login/refresh to SSO (avoids browser
 * CORS + keeps the token flow server-side) and exposes the current local user via /me.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final EntitlementService entitlementService;
    private final RestClient sso;

    public AuthController(UserService userService, EntitlementService entitlementService,
                          @Value("${sso.base-url}") String ssoBaseUrl) {
        this.userService = userService;
        this.entitlementService = entitlementService;
        this.sso = RestClient.create(ssoBaseUrl);
    }

    /** email/password → SSO access+refresh token (RS256). */
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Map<String, String> body) {
        return proxy("/auth/login", body);
    }

    /** refreshToken → new access token. */
    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@RequestBody Map<String, String> body) {
        return proxy("/auth/refresh", body);
    }

    private ResponseEntity<String> proxy(String path, Map<String, String> body) {
        try {
            String resp = sso.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        }
    }

    /** Current user. Pulls the authoritative plan from billing entitlements (session refresh). */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Long userId) {
        User user = userService.getById(userId);
        entitlementService.syncPlan(userId, user.getExternalId()); // billing = source of truth
        return UserDto.from(userService.getById(userId));
    }
}
