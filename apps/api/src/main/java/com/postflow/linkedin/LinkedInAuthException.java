package com.postflow.linkedin;

/**
 * LinkedIn access token is expired/invalid (401) — the caller should refresh via the
 * refresh token (if the app is approved for it) and retry, or mark reconnect-required.
 */
public class LinkedInAuthException extends LinkedInApiException {
    public LinkedInAuthException(String message) {
        super(message);
    }
}
