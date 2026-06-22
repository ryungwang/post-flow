package com.postflow.auth;

import com.postflow.auth.dto.AuthResponse;
import com.postflow.auth.dto.UserDto;
import com.postflow.user.User;
import com.postflow.user.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only convenience login so the app can be exercised in a real browser without a
 * configured Google client. Active ONLY under the {@code local} profile — never in prod.
 */
@RestController
@RequestMapping("/api/auth")
@Profile("local")
public class DevAuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public DevAuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/dev-login")
    public AuthResponse devLogin() {
        User user = userService.upsertFromGoogle("dev@postflow.local", "Dev Tester", null);
        String token = jwtService.issue(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user));
    }
}
