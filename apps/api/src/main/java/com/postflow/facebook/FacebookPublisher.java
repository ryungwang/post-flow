package com.postflow.facebook;

import com.postflow.social.Publisher;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

/**
 * Publishes to a Facebook Page (text, or an image Facebook fetches by URL) using the Page's
 * stored access token. Page tokens (from a long-lived user token) don't refresh here, so an
 * invalid-token error marks the account reconnect-required.
 */
@Component
public class FacebookPublisher implements Publisher {

    private final SocialAccountRepository repository;
    private final FacebookApiClient client;

    public FacebookPublisher(SocialAccountRepository repository, FacebookApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.FACEBOOK;
    }

    @Override
    public String publish(Long accountId, String text, String mediaUrl) {
        SocialAccount account = repository.findById(accountId)
                .orElseThrow(() -> new FacebookApiException("연결된 페이스북 페이지를 찾을 수 없어요."));
        try {
            return client.createPost(account.getExternalId(), account.getAccessToken(), text, mediaUrl);
        } catch (FacebookAuthException revoked) {
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
            client.deletePost(platformPostId, account.getAccessToken());
        } catch (FacebookAuthException revoked) {
            account.markReconnectRequired();
            repository.save(account);
            throw revoked;
        }
    }
}
