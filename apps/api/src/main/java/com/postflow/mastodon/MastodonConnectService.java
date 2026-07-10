package com.postflow.mastodon;

import com.postflow.mastodon.MastodonApiClient.MastodonAccount;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import com.postflow.user.PlanLimitException;
import com.postflow.user.PlanPolicy;
import com.postflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Connects a Mastodon account: normalize the instance URL, verify the personal access token,
 * and store the connection (instance + token, no password). Reconnect if the same account
 * (instance + account id) already exists; otherwise it is a new channel — gated by plan.
 */
@Service
public class MastodonConnectService {

    private static final SocialProvider MASTODON = SocialProvider.MASTODON;

    private final SocialAccountRepository repository;
    private final MastodonApiClient client;
    private final UserService userService;

    public MastodonConnectService(SocialAccountRepository repository, MastodonApiClient client,
                                  UserService userService) {
        this.repository = repository;
        this.client = client;
        this.userService = userService;
    }

    @Transactional
    public void connect(Long userId, String instanceInput, String token) {
        if (!StringUtils.hasText(instanceInput) || !StringUtils.hasText(token)) {
            throw new MastodonApiException("인스턴스 주소와 액세스 토큰을 모두 입력해 주세요.");
        }
        String instanceUrl = normalizeInstance(instanceInput);
        MastodonAccount profile = client.verifyCredentials(instanceUrl, token.trim());
        if (profile == null || profile.id() == null) {
            throw new MastodonApiException("마스토돈 계정을 확인할 수 없어요.");
        }
        // externalId = 인스턴스+계정id 조합(다른 인스턴스의 동일 id 충돌 방지).
        String externalId = instanceHost(instanceUrl) + ":" + profile.id();
        String acct = profile.acct() != null ? profile.acct() : profile.username();

        SocialAccount existing = repository
                .findByUserIdAndProviderAndExternalId(userId, MASTODON, externalId)
                .orElse(null);
        SocialAccount target;
        if (existing != null) {
            existing.reconnectMastodon(instanceUrl, externalId, acct,
                    profile.displayName(), profile.avatar(), token.trim());
            target = existing;
        } else {
            assertCanAddChannel(userId);
            target = repository.save(SocialAccount.connectMastodon(
                    userId, instanceUrl, externalId, acct,
                    profile.displayName(), profile.avatar(), token.trim()));
        }
        makeDefault(userId, target);
    }

    /** Ensure a scheme, strip trailing slash / path. Accepts "mastodon.social" or full URL. */
    static String normalizeInstance(String input) {
        String s = input.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://" + s;
        }
        // keep scheme + host only
        int schemeEnd = s.indexOf("://") + 3;
        int pathStart = s.indexOf('/', schemeEnd);
        if (pathStart >= 0) {
            s = s.substring(0, pathStart);
        }
        return s;
    }

    private static String instanceHost(String instanceUrl) {
        return instanceUrl.replaceFirst("^https?://", "");
    }

    private void assertCanAddChannel(Long userId) {
        boolean multi = PlanPolicy.canMultiAccount(userService.getById(userId).getPlan());
        if (!multi && repository.countByUserId(userId) >= 1) {
            throw new PlanLimitException("다중 채널 연결은 Pro 플랜부터 가능해요. (현재 플랜은 1채널)");
        }
    }

    private void makeDefault(Long userId, SocialAccount target) {
        for (SocialAccount a : repository.findByUserIdOrderByIdAsc(userId)) {
            a.setDefault(a.getId() != null && a.getId().equals(target.getId()));
        }
        target.setDefault(true);
    }
}
