package com.postflow.mastodon;

import com.postflow.mastodon.MastodonApiClient.MastodonStatus;
import com.postflow.social.Publisher;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

/**
 * Publishes to Mastodon (text + optional image) using the account's stored instance URL and
 * access token. Mastodon tokens don't expire on their own, so a 401 means the token was
 * revoked → the account is marked reconnect-required (no refresh flow).
 */
@Component
public class MastodonPublisher implements Publisher {

    private final SocialAccountRepository repository;
    private final MastodonApiClient client;

    public MastodonPublisher(SocialAccountRepository repository, MastodonApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.MASTODON;
    }

    @Override
    public String publish(Long accountId, String text, String mediaUrl) {
        SocialAccount account = repository.findById(accountId)
                .orElseThrow(() -> new MastodonApiException("연결된 마스토돈 계정을 찾을 수 없어요."));
        try {
            MastodonStatus status = client.createStatus(
                    account.getInstanceUrl(), account.getAccessToken(), text, mediaUrl);
            return status != null ? status.id() : null;
        } catch (MastodonAuthException revoked) {
            account.markReconnectRequired();
            repository.save(account);
            throw revoked;
        }
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
            client.deleteStatus(account.getInstanceUrl(), account.getAccessToken(), platformPostId);
        } catch (MastodonAuthException revoked) {
            account.markReconnectRequired();
            repository.save(account);
            throw revoked;
        }
    }
}
