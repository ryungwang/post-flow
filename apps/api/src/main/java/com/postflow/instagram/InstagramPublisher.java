package com.postflow.instagram;

import com.postflow.social.Publisher;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

/**
 * Publishes an image post to an Instagram Business account (container → publish). Instagram
 * feed posts require an image, so a target with no media fails with a clear message. The IG
 * Graph API has no delete endpoint for published media, so delete is a no-op (unsupported).
 */
@Component
public class InstagramPublisher implements Publisher {

    private final SocialAccountRepository repository;
    private final InstagramApiClient client;

    public InstagramPublisher(SocialAccountRepository repository, InstagramApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.INSTAGRAM;
    }

    @Override
    public String publish(Long accountId, String text, String mediaUrl) {
        SocialAccount account = repository.findById(accountId)
                .orElseThrow(() -> new InstagramApiException("연결된 인스타그램 계정을 찾을 수 없어요."));
        return client.publishImage(account.getExternalId(), account.getAccessToken(), text, mediaUrl);
    }

    // Instagram Graph API can't delete published media → deletePost stays a no-op (Publisher default).
}
