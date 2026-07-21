package com.postflow.instagram;

import com.postflow.instagram.InstagramApiClient.IgMedia;
import com.postflow.instagram.InstagramApiClient.IgProfile;
import com.postflow.instagram.dto.InstagramInsightsDto;
import com.postflow.instagram.dto.InstagramPostDto;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 인스타그램 전용 조회(내 게시물·인사이트). IG Graph API — 계정의 accessToken은 연결된 페이지
 * 토큰이고 externalId는 IG 비즈니스 계정 id다. 조회는 {@code instagram_manage_insights} 검수 필요.
 */
@Service
public class InstagramReadService {

    private static final int INSIGHTS_SAMPLE = 25;

    private final SocialAccountRepository repository;
    private final InstagramApiClient client;

    public InstagramReadService(SocialAccountRepository repository, InstagramApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public List<InstagramPostDto> posts(Long userId, int limit) {
        SocialAccount account = account(userId);
        List<IgMedia> media = client.getRecentMedia(
                account.getExternalId(), account.getAccessToken(), limit);
        return media.stream().map(InstagramReadService::toPostDto).toList();
    }

    @Transactional(readOnly = true)
    public InstagramInsightsDto insights(Long userId) {
        SocialAccount account = account(userId);
        IgProfile profile = client.getProfile(account.getExternalId(), account.getAccessToken());
        List<IgMedia> media = client.getRecentMedia(
                account.getExternalId(), account.getAccessToken(), INSIGHTS_SAMPLE);

        long likes = 0, comments = 0;
        for (IgMedia m : media) {
            likes += nz(m.likeCount());
            comments += nz(m.commentsCount());
        }
        return new InstagramInsightsDto(
                profile != null ? profile.username() : account.getUsername(),
                profile != null ? profile.profilePictureUrl() : null,
                profile != null ? nz(profile.followersCount()) : 0,
                profile != null ? nz(profile.followsCount()) : 0,
                profile != null ? nz(profile.mediaCount()) : 0,
                likes, comments, media.size());
    }

    private SocialAccount account(Long userId) {
        return repository.findByUserIdAndProviderOrderByIdAsc(userId, SocialProvider.INSTAGRAM).stream()
                .findFirst()
                .orElseThrow(() -> new InstagramApiException(
                        "연결된 인스타그램 계정이 없어요. Facebook 페이지 연결 시 함께 등록돼요."));
    }

    private static InstagramPostDto toPostDto(IgMedia m) {
        return new InstagramPostDto(
                m.id(),
                m.caption(),
                m.timestamp(),
                nz(m.likeCount()), nz(m.commentsCount()),
                m.mediaUrl(),
                m.permalink());
    }

    private static long nz(Integer i) {
        return i == null ? 0 : i;
    }
}
