package com.postflow.bluesky;

/**
 * AT Protocol session (from createSession / refreshSession). We persist only these
 * JWTs (never the app password). {@code did} is the durable account id.
 */
public record BlueskySession(String did, String handle, String accessJwt, String refreshJwt) {
}
