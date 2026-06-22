package com.postflow.auth;

import com.postflow.auth.dto.AuthResponse;
import com.postflow.auth.dto.GoogleLoginRequest;
import com.postflow.auth.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Exchange a Google ID token for an app JWT (PRD: POST /api/auth/google). */
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.idToken());
    }

    /** Current authenticated user (PRD: GET /api/auth/me). */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Long userId) {
        return authService.me(userId);
    }
}
