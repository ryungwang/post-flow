package com.postflow.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies Google ID tokens (signature, issuer, expiry, audience) via Google's verifier,
 * which fetches and caches Google's public keys. Returns the trusted user identity.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(AuthProperties properties) {
        String clientId = properties.google() != null ? properties.google().clientId() : null;
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUserInfo verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new InvalidTokenException("Invalid Google ID token");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleUserInfo(
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture"));
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new InvalidTokenException("Failed to verify Google ID token");
        }
    }
}
