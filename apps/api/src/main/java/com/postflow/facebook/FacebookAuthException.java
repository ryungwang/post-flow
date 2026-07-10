package com.postflow.facebook;

/**
 * Facebook Page access token is invalid/expired (OAuthException / 190). The account should be
 * marked reconnect-required so the user re-authorizes (page tokens have no refresh flow here).
 */
public class FacebookAuthException extends FacebookApiException {
    public FacebookAuthException(String message) {
        super(message);
    }
}
