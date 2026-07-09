package com.postflow.bluesky;

import com.postflow.social.Publisher;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

/**
 * Publishes to Bluesky (AT Protocol). MVP = text only (media is a follow-up — it needs
 * blob upload). Uses the stored session accessJwt; on expiry, refreshes via refreshJwt,
 * persists the rotated tokens, and retries once. If refresh fails, the account is marked
 * reconnect-required (mirrors Threads reconnect handling).
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
            return client.createTextPost(account.getExternalId(), account.getAccessToken(), text);
        } catch (BlueskyAuthException expired) {
            try {
                BlueskySession refreshed = client.refreshSession(account.getRefreshToken());
                account.applyBlueskySession(refreshed.accessJwt(), refreshed.refreshJwt());
                repository.save(account); // merge: persist rotated tokens
                return client.createTextPost(account.getExternalId(), refreshed.accessJwt(), text);
            } catch (BlueskyAuthException refreshFailed) {
                account.markReconnectRequired();
                repository.save(account);
                throw refreshFailed;
            }
        }
    }
}
