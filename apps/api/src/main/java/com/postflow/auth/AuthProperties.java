package com.postflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Auth config.
 *
 * @param google  Google OAuth client config (audience for ID-token verification)
 * @param jwt     app-issued JWT config
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Google google,
        Jwt jwt
) {
    public record Google(
            /** Google OAuth client id — ID tokens must carry this as audience */
            String clientId
    ) {
    }

    public record Jwt(
            /** HMAC signing secret (>= 32 bytes); injected via env in prod */
            String secret,
            /** token lifetime */
            Duration expiration
    ) {
    }
}
