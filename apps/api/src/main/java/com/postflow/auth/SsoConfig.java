package com.postflow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

/**
 * Verifies synub-sso RS256 access tokens via its JWKS (kid rotation handled automatically).
 * Validates signature + issuer + that this service ({@code synub-postflow}) is in the token audience.
 */
@Configuration
public class SsoConfig {

    @Bean
    public JwtDecoder ssoJwtDecoder(@Value("${synub.sso.base-url}") String ssoBaseUrl,
                                    @Value("${synub.sso.issuer}") String issuer,
                                    @Value("${synub.sso.audience}") String audience) {
        // jwks는 SSO base-url에서 파생(office 컨벤션) — 별도 URL env 없음.
        String jwksUri = ssoBaseUrl + "/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = jwt ->
                jwt.getAudience() != null && jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "audience missing " + audience, null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(withIssuer, withAudience)));
        return decoder;
    }
}
