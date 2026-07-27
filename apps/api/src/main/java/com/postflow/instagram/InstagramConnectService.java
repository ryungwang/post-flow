package com.postflow.instagram;

import com.postflow.instagram.InstagramLoginClient.IgLoginProfile;
import com.postflow.instagram.InstagramLoginClient.IgLoginToken;
import com.postflow.instagram.InstagramLoginClient.IgLongLived;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import com.postflow.user.PlanPolicy;
import com.postflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * "Instagram API with Instagram login" 연결 — 페이스북 페이지 없이 IG 비즈니스/크리에이터 계정을
 * 직접 채널로 등록한다. 흐름: 인가코드 → 단기토큰 → 장기(60일)토큰 → 프로필(/me) → upsert.
 * 개인(PERSONAL) 계정은 Meta가 발행 API를 안 열어주므로 명확한 메시지로 거절한다.
 * 채널 호스트(graph.instagram.com)는 계정의 instanceUrl 에 저장돼 발행 시 라우팅된다.
 */
@Service
public class InstagramConnectService {

    private static final SocialProvider INSTAGRAM = SocialProvider.INSTAGRAM;

    private final SocialAccountRepository repository;
    private final InstagramLoginClient loginClient;
    private final InstagramProperties props;
    private final UserService userService;

    public InstagramConnectService(SocialAccountRepository repository, InstagramLoginClient loginClient,
                                   InstagramProperties props, UserService userService) {
        this.repository = repository;
        this.loginClient = loginClient;
        this.props = props;
        this.userService = userService;
    }

    @Transactional
    public void connectFromCode(Long userId, String code) {
        IgLoginToken shortTok = loginClient.exchangeCode(code);
        IgLongLived longTok = loginClient.exchangeForLongLived(shortTok.accessToken());
        String token = longTok.accessToken();
        IgLoginProfile profile = loginClient.getProfile(token);

        if (profile == null || profile.accountId() == null) {
            throw new InstagramApiException("인스타그램 계정 정보를 가져오지 못했어요. 다시 시도해 주세요.");
        }
        if (!profile.canPublish()) {
            throw new InstagramApiException(
                    "개인(Personal) 계정은 자동 발행을 지원하지 않아요. 인스타그램 앱에서 "
                            + "프로페셔널(비즈니스·크리에이터) 계정으로 전환한 뒤 다시 연결해 주세요.");
        }

        String igUserId = profile.accountId();
        String graphHost = props.graphBaseUrlOrDefault();
        Instant expiresAt = longTok.expiresIn() != null
                ? Instant.now().plusSeconds(longTok.expiresIn()) : null;

        SocialAccount existing = repository
                .findByUserIdAndProviderAndExternalId(userId, INSTAGRAM, igUserId)
                .orElse(null);
        if (existing != null) {
            existing.reconnectInstagramLogin(
                    profile.username(), profile.profilePictureUrl(), token, graphHost, expiresAt);
            makeDefault(userId, existing);
            return;
        }

        boolean multi = PlanPolicy.canMultiAccount(userService.getById(userId).getPlan());
        if (!multi && repository.countByUserId(userId) >= 1) {
            throw new InstagramApiException(
                    "무료 플랜은 채널 1개만 연결할 수 있어요. 기존 채널을 해제하거나 Pro 플랜에서 여러 채널을 연결하세요.");
        }
        SocialAccount saved = repository.save(SocialAccount.connectInstagramLogin(
                userId, igUserId, profile.username(), profile.profilePictureUrl(),
                token, graphHost, expiresAt));
        makeDefault(userId, saved);
    }

    private void makeDefault(Long userId, SocialAccount target) {
        for (SocialAccount a : repository.findByUserIdOrderByIdAsc(userId)) {
            a.setDefault(a.getId() != null && a.getId().equals(target.getId()));
        }
        target.setDefault(true);
    }
}
