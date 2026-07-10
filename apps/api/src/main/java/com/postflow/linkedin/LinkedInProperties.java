package com.postflow.linkedin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LinkedIn (OAuth2) integration config. Requires a LinkedIn Developer app with the
 * "Sign In with LinkedIn using OpenID Connect" + "Share on LinkedIn" products approved.
 * Credentials are injected from env (empty by default until keys are provisioned).
 *
 * @param clientId         LinkedIn app client id
 * @param clientSecret     LinkedIn app client secret
 * @param redirectUri      OAuth redirect (must match the app's Authorized redirect URL)
 * @param scopes           space-separated OAuth scopes
 * @param authorizeBaseUrl user-facing authorize host (www.linkedin.com)
 * @param apiBaseUrl       REST API host (api.linkedin.com)
 * @param restliVersion    LinkedIn-Version header for the versioned /rest APIs (YYYYMM)
 * @param frontendRedirectUrl where the callback sends the browser after connecting
 */
@ConfigurationProperties(prefix = "linkedin")
public record LinkedInProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String scopes,
        String authorizeBaseUrl,
        String apiBaseUrl,
        String restliVersion,
        String frontendRedirectUrl
) {
    public String scopesOrDefault() {
        return scopes == null || scopes.isBlank() ? "openid profile w_member_social" : scopes;
    }

    public String authorizeBaseUrlOrDefault() {
        return authorizeBaseUrl == null || authorizeBaseUrl.isBlank()
                ? "https://www.linkedin.com" : authorizeBaseUrl;
    }

    public String apiBaseUrlOrDefault() {
        return apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://api.linkedin.com" : apiBaseUrl;
    }

    public String restliVersionOrDefault() {
        return restliVersion == null || restliVersion.isBlank() ? "202401" : restliVersion;
    }

    public boolean configured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
