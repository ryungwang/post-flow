package com.postflow.bluesky;

import com.postflow.social.Publisher;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

/**
 * Publishes to Bluesky (AT Protocol) — text + image (image is uploaded as a blob and
 * embedded; non-image media like video is posted as text-only for now). Uses the stored
 * session accessJwt; on expiry, refreshes via refreshJwt, persists the rotated tokens, and
 * retries once. If refresh fails, the account is marked reconnect-required.
 */
@Component
public class BlueskyPublisher implements Publisher {

    private final SocialAccountRepository repository;
    private final BlueskyApiClient client;

    public BlueskyPublisher(SocialAccountRepository repository, BlueskyApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.BLUESKY;
    }

    @Override
    public String publish(Long accountId, String text, String mediaUrl) {
        SocialAccount account = repository.findById(accountId)
                .orElseThrow(() -> new BlueskyApiException("연결된 블루스카이 계정을 찾을 수 없어요."));
        try {
            return client.createPost(account.getExternalId(), account.getAccessToken(), text, mediaUrl);
        } catch (BlueskyAuthException expired) {
            try {
                BlueskySession refreshed = client.refreshSession(account.getRefreshToken());
                account.applyBlueskySession(refreshed.accessJwt(), refreshed.refreshJwt());
                repository.save(account); // merge: persist rotated tokens
                return client.createPost(account.getExternalId(), refreshed.accessJwt(), text, mediaUrl);
            } catch (BlueskyAuthException refreshFailed) {
                account.markReconnectRequired();
                repository.save(account);
                throw refreshFailed;
            }
        }
    }

    @Override
    public void deletePost(Long accountId, String platformPostId) {
        if (platformPostId == null || platformPostId.isBlank()) {
            return;
        }
        String rkey = platformPostId.substring(platformPostId.lastIndexOf('/') + 1); // at://did/coll/rkey
        SocialAccount account = repository.findById(accountId).orElse(null);
        if (account == null) {
            return;
        }
        try {
            client.deleteRecord(account.getExternalId(), account.getAccessToken(), rkey);
        } catch (BlueskyAuthException expired) {
            BlueskySession refreshed = client.refreshSession(account.getRefreshToken());
            account.applyBlueskySession(refreshed.accessJwt(), refreshed.refreshJwt());
            repository.save(account);
            client.deleteRecord(account.getExternalId(), refreshed.accessJwt(), rkey);
        }
    }
}
