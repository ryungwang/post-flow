package com.postflow.linkedin;

import com.postflow.social.Publisher;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publishes to LinkedIn (member feed) via the versioned /rest/posts API — text only for now
 * (image upload is a later increment). Uses the stored access token; on 401 it refreshes via
 * the refresh token (approved apps only), persists rotated tokens, and retries once. If no
 * refresh token is available or refresh fails, the account is marked reconnect-required.
 */
@Component
public class LinkedInPublisher implements Publisher {

    private final SocialAccountRepository repository;
    private final LinkedInApiClient client;

    public LinkedInPublisher(SocialAccountRepository repository, LinkedInApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.LINKEDIN;
    }

    @Override
    public String publish(Long accountId, String text, String mediaUrl) {
        SocialAccount account = repository.findById(accountId)
                .orElseThrow(() -> new LinkedInApiException("연결된 링크드인 계정을 찾을 수 없어요."));
        String author = authorUrn(account);
        try {
            return client.createPost(author, account.getAccessToken(), text, mediaUrl);
        } catch (LinkedInAuthException expired) {
            String token = refreshOrFail(account);
            return client.createPost(author, token, text, mediaUrl);
        }
    }

    /** Org channels store the full {@code urn:li:organization:…}; person channels store the bare sub. */
    private static String authorUrn(SocialAccount account) {
        String ext = account.getExternalId();
        return ext != null && ext.startsWith("urn:") ? ext : "urn:li:person:" + ext;
    }

    @Override
    public void deletePost(Long accountId, String platformPostId) {
        if (platformPostId == null || platformPostId.isBlank()) {
            return;
        }
        SocialAccount account = repository.findById(accountId).orElse(null);
        if (account == null) {
            return;
        }
        try {
            client.deletePost(platformPostId, account.getAccessToken());
        } catch (LinkedInAuthException expired) {
            String token = refreshOrFail(account);
            client.deletePost(platformPostId, token);
        }
    }

    /** Refresh the access token in place, or mark reconnect-required and rethrow. */
    private String refreshOrFail(SocialAccount account) {
        if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
            account.markReconnectRequired();
            repository.save(account);
            throw new LinkedInAuthException("링크드인 재연결이 필요해요.");
        }
        try {
            var refreshed = client.refreshToken(account.getRefreshToken());
            account.applyLinkedinToken(refreshed.accessToken(), refreshed.refreshToken(),
                    Instant.now().plusSeconds(refreshed.expiresIn() != null ? refreshed.expiresIn() : 0L));
            repository.save(account);
            return refreshed.accessToken();
        } catch (LinkedInApiException refreshFailed) {
            account.markReconnectRequired();
            repository.save(account);
            throw refreshFailed;
        }
    }
}
