package com.postflow.bluesky;

/**
 * Bluesky access token is expired/invalid — the caller should refresh the session
 * (via refreshJwt) and retry, or mark the account reconnect-required.
 */
public class BlueskyAuthException extends BlueskyApiException {
    public BlueskyAuthException(String message) {
        super(message);
    }
}
