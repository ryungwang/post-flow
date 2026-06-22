package com.postflow.auth;

import com.postflow.auth.dto.AuthResponse;
import com.postflow.auth.dto.UserDto;
import com.postflow.user.User;
import com.postflow.user.UserService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserService userService;
    private final JwtService jwtService;

    public AuthService(GoogleTokenVerifier googleTokenVerifier,
                       UserService userService,
                       JwtService jwtService) {
        this.googleTokenVerifier = googleTokenVerifier;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /** Verify a Google ID token, upsert the user, and issue an app JWT. */
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleUserInfo info = googleTokenVerifier.verify(idToken);
        User user = userService.upsertFromGoogle(info.email(), info.name(), info.pictureUrl());
        String token = jwtService.issue(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user));
    }

    public UserDto me(Long userId) {
        return UserDto.from(userService.getById(userId));
    }
}
