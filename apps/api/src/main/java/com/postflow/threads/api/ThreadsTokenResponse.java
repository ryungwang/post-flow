package com.postflow.threads.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Covers short-lived exchange (carries user_id) and long-lived/refresh (carries expires_in). */
public record ThreadsTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("user_id") String userId
) {
}
