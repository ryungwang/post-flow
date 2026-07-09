package com.postflow.bluesky;

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
 * Connects a Bluesky account: exchange handle + app password for a session and store the
 * session JWTs (never the app password). Reconnect if the same account (DID) already
 * exists; otherwise it is a new channel — gated by plan (channels across all providers).
 */
@Service
public class BlueskyConnectService {

    private final SocialAccountRepository repository;
    private final BlueskyApiClient client;
    private final UserService userService;

    public BlueskyConnectService(SocialAccountRepository repository, BlueskyApiClient client,
                                 UserService userService) {
        this.repository = repository;
        this.client = client;
        this.userService = userService;
    }

    @Transactional
    public void connect(Long userId, String handle, String appPassword) {
        if (!StringUtils.hasText(handle) || !StringUtils.hasText(appPassword)) {
            throw new BlueskyApiException("핸들과 앱 비밀번호를 모두 입력해 주세요.");
        }
        String identifier = handle.trim().replaceFirst("^@", "");
        BlueskySession session = client.createSession(identifier, appPassword.trim());

        SocialAccount existing = repository
                .findByUserIdAndProviderAndExternalId(userId, SocialProvider.BLUESKY, session.did())
                .orElse(null);
        SocialAccount target;
        if (existing != null) {
            existing.reconnectBluesky(session.did(), session.handle(),
                    session.accessJwt(), session.refreshJwt());
            target = existing;
        } else {
            assertCanAddChannel(userId); // new channel → plan gate (cross-provider)
            target = repository.save(SocialAccount.connectBluesky(
                    userId, session.did(), session.handle(),
                    session.accessJwt(), session.refreshJwt()));
        }
        makeDefault(userId, target); // newly connected becomes the active channel
    }

    /** Adding a new channel (any provider) beyond the first requires the Pro plan. */
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
