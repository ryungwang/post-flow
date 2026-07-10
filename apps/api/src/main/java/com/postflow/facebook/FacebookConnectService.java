package com.postflow.facebook;

import com.postflow.facebook.FacebookApiClient.FbPage;
import com.postflow.facebook.FacebookApiClient.FbToken;
import com.postflow.instagram.InstagramApiClient;
import com.postflow.instagram.InstagramApiClient.IgAccount;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import com.postflow.user.PlanPolicy;
import com.postflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Connects Facebook Page channels: exchange the OAuth code, list the Pages the user manages,
 * and upsert each as a channel (its own Page token). Existing pages are reconnected (token
 * refreshed) unconditionally; new pages are added subject to the plan's channel limit
 * (Free = 1 channel across all providers, Pro = many).
 */
@Service
public class FacebookConnectService {

    private static final SocialProvider FACEBOOK = SocialProvider.FACEBOOK;
    private static final SocialProvider INSTAGRAM = SocialProvider.INSTAGRAM;

    private final SocialAccountRepository repository;
    private final FacebookApiClient client;
    private final InstagramApiClient instagramClient;
    private final UserService userService;

    public FacebookConnectService(SocialAccountRepository repository, FacebookApiClient client,
                                  InstagramApiClient instagramClient, UserService userService) {
        this.repository = repository;
        this.client = client;
        this.instagramClient = instagramClient;
        this.userService = userService;
    }

    @Transactional
    public void connectFromCode(Long userId, String code) {
        FbToken token = client.exchangeCode(code);
        List<FbPage> pages = client.getPages(token.accessToken());
        if (pages.isEmpty()) {
            throw new FacebookApiException("관리하는 페이스북 페이지가 없어요. 페이지 관리자 권한이 필요해요.");
        }
        boolean multi = PlanPolicy.canMultiAccount(userService.getById(userId).getPlan());
        long channelCount = repository.countByUserId(userId);

        SocialAccount firstConnected = null;
        for (FbPage page : pages) {
            SocialAccount existing = repository
                    .findByUserIdAndProviderAndExternalId(userId, FACEBOOK, page.id())
                    .orElse(null);
            if (existing != null) {
                existing.reconnectFacebookPage(page.name(), page.pictureUrl(), page.accessToken());
                if (firstConnected == null) {
                    firstConnected = existing;
                }
            } else if (multi || channelCount < 1) {
                // new page — respect the plan channel limit (Free = 1 total across providers)
                SocialAccount saved = repository.save(SocialAccount.connectFacebookPage(
                        userId, page.id(), page.name(), page.pictureUrl(), page.accessToken()));
                channelCount++;
                if (firstConnected == null) {
                    firstConnected = saved;
                }
            }
            // Best-effort: register the linked Instagram Business account as a channel too.
            channelCount = connectInstagramFor(userId, page, multi, channelCount);
        }
        if (firstConnected == null) {
            throw new FacebookApiException("페이지를 연결하지 못했어요. Pro 플랜에서 여러 채널을 연결할 수 있어요.");
        }
        makeDefault(userId, firstConnected);
    }

    /**
     * Discover the Instagram Business account linked to a Page and upsert it as an INSTAGRAM
     * channel (best-effort — needs instagram_basic + instagram_content_publish scope). Returns
     * the possibly-incremented channel count.
     */
    private long connectInstagramFor(Long userId, FbPage page, boolean multi, long channelCount) {
        IgAccount ig = instagramClient.discoverIgAccount(page.id(), page.accessToken());
        if (ig == null || ig.id() == null) {
            return channelCount;
        }
        SocialAccount existing = repository
                .findByUserIdAndProviderAndExternalId(userId, INSTAGRAM, ig.id())
                .orElse(null);
        if (existing != null) {
            existing.reconnectInstagram(ig.username(), ig.profilePictureUrl(), page.accessToken());
            return channelCount;
        }
        if (!multi && channelCount >= 1) {
            return channelCount; // Free plan channel limit
        }
        repository.save(SocialAccount.connectInstagram(
                userId, ig.id(), ig.username(), ig.profilePictureUrl(), page.accessToken()));
        return channelCount + 1;
    }

    private void makeDefault(Long userId, SocialAccount target) {
        for (SocialAccount a : repository.findByUserIdOrderByIdAsc(userId)) {
            a.setDefault(a.getId() != null && a.getId().equals(target.getId()));
        }
        target.setDefault(true);
    }
}
