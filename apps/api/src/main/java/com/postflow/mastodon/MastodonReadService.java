package com.postflow.mastodon;

import com.postflow.mastodon.MastodonApiClient.MastodonAccount;
import com.postflow.mastodon.MastodonApiClient.MastodonAttachment;
import com.postflow.mastodon.MastodonApiClient.MastodonNotification;
import com.postflow.mastodon.MastodonApiClient.MastodonStatusItem;
import com.postflow.mastodon.dto.MastodonInsightsDto;
import com.postflow.mastodon.dto.MastodonMentionDto;
import com.postflow.mastodon.dto.MastodonPostDto;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마스토돈 전용 조회(내 게시물·인사이트·멘션). 마스토돈은 앱 검수도 유료 티어도 없어서
 * 토큰의 read 범위만 있으면 바로 동작한다. 토큰이 회수되면(401) 재연결 필요로 표시한다.
 */
@Service
public class MastodonReadService {

    private static final int INSIGHTS_SAMPLE = 40;

    private final SocialAccountRepository repository;
    private final MastodonApiClient client;

    public MastodonReadService(SocialAccountRepository repository, MastodonApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Transactional
    public List<MastodonPostDto> posts(Long userId, int limit) {
        SocialAccount account = account(userId);
        List<MastodonStatusItem> statuses = call(account, () -> client.getAccountStatuses(
                account.getInstanceUrl(), account.getAccessToken(), accountId(account), limit));
        return statuses.stream().map(MastodonReadService::toPostDto).toList();
    }

    @Transactional
    public MastodonInsightsDto insights(Long userId) {
        SocialAccount account = account(userId);
        MastodonAccount profile = call(account, () -> client.verifyCredentials(
                account.getInstanceUrl(), account.getAccessToken()));
        List<MastodonStatusItem> statuses = call(account, () -> client.getAccountStatuses(
                account.getInstanceUrl(), account.getAccessToken(), accountId(account), INSIGHTS_SAMPLE));

        long favourites = 0, reblogs = 0, replies = 0;
        for (MastodonStatusItem s : statuses) {
            favourites += nz(s.favouritesCount());
            reblogs += nz(s.reblogsCount());
            replies += nz(s.repliesCount());
        }
        return new MastodonInsightsDto(
                profile != null ? profile.acct() : account.getHandle(),
                profile != null ? profile.displayName() : null,
                profile != null ? profile.avatar() : null,
                instanceHost(account),
                profile != null ? nz(profile.followersCount()) : 0,
                profile != null ? nz(profile.followingCount()) : 0,
                profile != null ? nz(profile.statusesCount()) : 0,
                favourites, reblogs, replies, statuses.size());
    }

    @Transactional
    public List<MastodonMentionDto> mentions(Long userId, int limit) {
        SocialAccount account = account(userId);
        List<MastodonNotification> notifications = call(account, () -> client.getMentions(
                account.getInstanceUrl(), account.getAccessToken(), limit));
        return notifications.stream()
                .filter(n -> n.status() != null)
                .map(MastodonReadService::toMentionDto)
                .toList();
    }

    /** 토큰 회수(401)는 재연결 필요로 표시해 다른 플랫폼과 동선을 맞춘다. */
    private <T> T call(SocialAccount account, java.util.function.Supplier<T> apiCall) {
        try {
            return apiCall.get();
        } catch (MastodonAuthException revoked) {
            account.markReconnectRequired();
            repository.save(account);
            throw revoked;
        }
    }

    private SocialAccount account(Long userId) {
        return repository.findByUserIdAndProviderOrderByIdAsc(userId, SocialProvider.MASTODON).stream()
                .findFirst()
                .orElseThrow(() -> new MastodonApiException(
                        "연결된 마스토돈 계정이 없어요. 먼저 채널을 연결해 주세요."));
    }

    /** externalId = "host:accountId" (인스턴스 간 id 충돌 방지) → 인스턴스에 보낼 계정 id만 분리. */
    private static String accountId(SocialAccount account) {
        String externalId = account.getExternalId();
        int colon = externalId.lastIndexOf(':');
        return colon >= 0 ? externalId.substring(colon + 1) : externalId;
    }

    private static String instanceHost(SocialAccount account) {
        String url = account.getInstanceUrl();
        return url == null ? null : url.replaceFirst("^https?://", "");
    }

    private static MastodonPostDto toPostDto(MastodonStatusItem s) {
        return new MastodonPostDto(
                s.id(),
                stripHtml(s.content()),
                s.createdAt(),
                nz(s.favouritesCount()), nz(s.reblogsCount()), nz(s.repliesCount()),
                firstImage(s),
                s.url());
    }

    private static MastodonMentionDto toMentionDto(MastodonNotification n) {
        MastodonAccount author = n.account();
        return new MastodonMentionDto(
                n.id(),
                n.status().id(),
                author != null ? author.acct() : null,
                author != null ? author.displayName() : null,
                author != null ? author.avatar() : null,
                stripHtml(n.status().content()),
                n.createdAt(),
                n.status().url());
    }

    private static String firstImage(MastodonStatusItem s) {
        if (s.mediaAttachments() == null) {
            return null;
        }
        return s.mediaAttachments().stream()
                .filter(a -> "image".equals(a.type()))
                .map(a -> a.previewUrl() != null ? a.previewUrl() : a.url())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 마스토돈 본문은 HTML이라 목록 표시용 평문으로 바꾼다. 문단·줄바꿈은 개행으로 살리고
     * 나머지 태그는 제거한 뒤 기본 엔티티만 되돌린다.
     */
    static String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("<[^>]+>", "");
        return text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .trim();
    }

    private static long nz(Integer i) {
        return i == null ? 0 : i;
    }
}
