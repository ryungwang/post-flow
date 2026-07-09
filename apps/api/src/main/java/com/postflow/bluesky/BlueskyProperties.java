package com.postflow.bluesky;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bluesky (AT Protocol) config. Default PDS host is bsky.social. Auth is app-password
 * based (no OAuth app registration), so there are no client id/secret here.
 */
@ConfigurationProperties(prefix = "bluesky")
public record BlueskyProperties(String baseUrl) {
    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank() ? "https://bsky.social" : baseUrl;
    }
}
