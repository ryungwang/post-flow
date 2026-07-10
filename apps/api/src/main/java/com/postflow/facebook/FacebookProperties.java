package com.postflow.facebook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Facebook (Meta) Pages integration config. Requires a Meta app with Facebook Login +
 * the {@code pages_manage_posts} / {@code pages_show_list} / {@code pages_read_engagement}
 * permissions (App Review). Credentials come from env (empty until provisioned).
 *
 * @param appId               Meta app id (distinct from the Threads app)
 * @param appSecret           Meta app secret
 * @param redirectUri         OAuth redirect (must match the app's Valid OAuth Redirect URIs)
 * @param scopes              comma-separated Facebook permissions
 * @param graphBaseUrl        Graph API host (graph.facebook.com)
 * @param dialogBaseUrl       user-facing login dialog host (www.facebook.com)
 * @param apiVersion          Graph API version (e.g. v21.0)
 * @param frontendRedirectUrl where the callback sends the browser after connecting
 */
@ConfigurationProperties(prefix = "facebook")
public record FacebookProperties(
        String appId,
        String appSecret,
        String redirectUri,
        String scopes,
        String graphBaseUrl,
        String dialogBaseUrl,
        String apiVersion,
        String frontendRedirectUrl
) {
    public String scopesOrDefault() {
        return scopes == null || scopes.isBlank()
                ? "pages_show_list,pages_manage_posts,pages_read_engagement" : scopes;
    }

    public String graphBaseUrlOrDefault() {
        return graphBaseUrl == null || graphBaseUrl.isBlank()
                ? "https://graph.facebook.com" : graphBaseUrl;
    }

    public String dialogBaseUrlOrDefault() {
        return dialogBaseUrl == null || dialogBaseUrl.isBlank()
                ? "https://www.facebook.com" : dialogBaseUrl;
    }

    public String apiVersionOrDefault() {
        return apiVersion == null || apiVersion.isBlank() ? "v21.0" : apiVersion;
    }
}
