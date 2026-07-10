package com.postflow.mastodon;

/**
 * Mastodon access token is invalid/revoked (401). Tokens don't expire on their own, so the
 * account should be marked reconnect-required (the user must issue a new token).
 */
public class MastodonAuthException extends MastodonApiException {
    public MastodonAuthException(String message) {
        super(message);
    }
}
