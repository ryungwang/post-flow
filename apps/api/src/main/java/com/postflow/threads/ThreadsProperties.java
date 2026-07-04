package com.postflow.threads;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Threads (Meta) integration config. Note: the Threads app id/secret are distinct from
 * any Facebook app credentials (common pitfall). See PRD → Research Notes → Threads API.
 *
 * @param appId               Threads app id
 * @param appSecret           Threads app secret
 * @param redirectUri         OAuth redirect (must match Meta app settings)
 * @param scopes              comma-separated scopes
 * @param authorizeBaseUrl    user-facing authorize host (threads.net)
 * @param graphBaseUrl        API host (graph.threads.net)
 * @param frontendRedirectUrl where the callback sends the browser after connecting
 */
@ConfigurationProperties(prefix = "threads")
public record ThreadsProperties(
        String appId,
        String appSecret,
        String redirectUri,
        String scopes,
        String authorizeBaseUrl,
        String graphBaseUrl,
        String frontendRedirectUrl
) {
    public String scopesOrDefault() {
        return scopes == null || scopes.isBlank()
                ? "threads_basic,threads_content_publish,threads_manage_insights,threads_manage_replies"
                : scopes;
    }

    public String authorizeBaseUrlOrDefault() {
        return authorizeBaseUrl == null || authorizeBaseUrl.isBlank()
                ? "https://threads.net" : authorizeBaseUrl;
    }

    public String graphBaseUrlOrDefault() {
        return graphBaseUrl == null || graphBaseUrl.isBlank()
                ? "https://graph.threads.net" : graphBaseUrl;
    }

    /** API 베이스 URL — redirectUri(.../threads/callback)에서 파생. data-deletion 상태 URL 등에 사용. */
    public String apiBaseUrl() {
        String uri = redirectUri == null ? "" : redirectUri;
        int idx = uri.indexOf("/threads/");
        return idx > 0 ? uri.substring(0, idx) : "http://localhost:8080";
    }
}
