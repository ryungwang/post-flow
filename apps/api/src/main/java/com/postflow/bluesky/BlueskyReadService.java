package com.postflow.bluesky;

import com.postflow.bluesky.BlueskyApiClient.BskyFeed;
import com.postflow.bluesky.BlueskyApiClient.BskyPost;
import com.postflow.bluesky.BlueskyApiClient.BskyProfile;
import com.postflow.bluesky.dto.BlueskyInsightsDto;
import com.postflow.bluesky.dto.BlueskyPostDto;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Bluesky 전용 조회(내 게시물·인사이트) — 공개 AppView 기반, 앱 검수 불필요. */
@Service
public class BlueskyReadService {

    private static final int INSIGHTS_SAMPLE = 50;

    private final SocialAccountRepository repository;
    private final BlueskyApiClient client;

    public BlueskyReadService(SocialAccountRepository repository, BlueskyApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public List<BlueskyPostDto> posts(Long userId, int limit) {
        SocialAccount account = account(userId);
        BskyFeed feed = client.getAuthorFeed(account.getExternalId(), limit);
        if (feed == null || feed.feed() == null) {
            return List.of();
        }
        return feed.feed().stream()
                .filter(item -> item.post() != null)
                .map(item -> toPostDto(item.post(), account.getHandle()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BlueskyInsightsDto insights(Long userId) {
        SocialAccount account = account(userId);
        BskyProfile profile = client.getProfile(account.getExternalId());
        BskyFeed feed = client.getAuthorFeed(account.getExternalId(), INSIGHTS_SAMPLE);
        long likes = 0, reposts = 0, replies = 0;
        int sampled = 0;
        if (feed != null && feed.feed() != null) {
            for (var item : feed.feed()) {
                BskyPost post = item.post();
                if (post == null) continue;
                likes += nz(post.likeCount());
                reposts += nz(post.repostCount());
                replies += nz(post.replyCount());
                sampled++;
            }
        }
        return new BlueskyInsightsDto(
                profile != null ? profile.handle() : account.getHandle(),
                profile != null ? profile.displayName() : null,
                profile != null ? profile.avatar() : null,
                profile != null ? nz(profile.followersCount()) : 0,
                profile != null ? nz(profile.postsCount()) : 0,
                likes, reposts, replies, sampled);
    }

    private SocialAccount account(Long userId) {
        return repository.findByUserIdAndProviderOrderByIdAsc(userId, SocialProvider.BLUESKY).stream()
                .findFirst()
                .orElseThrow(() -> new BlueskyApiException("연결된 블루스카이 계정이 없어요. 먼저 채널을 연결해 주세요."));
    }

    private BlueskyPostDto toPostDto(BskyPost post, String handle) {
        String uri = post.uri() == null ? "" : post.uri();
        String rkey = uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1) : uri;
        String permalink = "https://bsky.app/profile/" + handle + "/post/" + rkey;
        String imageUrl = (post.embed() != null && post.embed().images() != null
                && !post.embed().images().isEmpty()) ? post.embed().images().get(0).thumb() : null;
        return new BlueskyPostDto(
                rkey,
                post.record() != null ? post.record().text() : null,
                post.record() != null ? post.record().createdAt() : null,
                nz(post.likeCount()), nz(post.repostCount()), nz(post.replyCount()),
                imageUrl, permalink);
    }

    private static long nz(Integer i) {
        return i == null ? 0 : i;
    }
}
