package com.postflow.linkedin;

import com.postflow.linkedin.LinkedInApiClient.TokenResponse;
import com.postflow.linkedin.LinkedInApiClient.UserInfo;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import com.postflow.user.PlanLimitException;
import com.postflow.user.PlanPolicy;
import com.postflow.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Connects a LinkedIn account: exchange the OAuth code for tokens, read the member profile
 * via OpenID {@code userinfo}, and store the connection (access + refresh token). Reconnect
 * if the same member already exists; otherwise it is a new channel — gated by plan
 * (channels counted across all providers).
 */
@Service
public class LinkedInConnectService {

    private static final SocialProvider LINKEDIN = SocialProvider.LINKEDIN;
    private static final Logger log = LoggerFactory.getLogger(LinkedInConnectService.class);

    private final SocialAccountRepository repository;
    private final LinkedInApiClient client;
    private final UserService userService;

    public LinkedInConnectService(SocialAccountRepository repository, LinkedInApiClient client,
                                  UserService userService) {
        this.repository = repository;
        this.client = client;
        this.userService = userService;
    }

    @Transactional
    public void connectFromCode(Long userId, String code) {
        TokenResponse token = client.exchangeCode(code);
        UserInfo profile = client.userInfo(token.accessToken());
        if (profile == null || profile.sub() == null) {
            throw new LinkedInApiException("링크드인 프로필을 확인할 수 없어요.");
        }
        Instant expiresAt = expiryFrom(token.expiresIn());

        SocialAccount existing = repository
                .findByUserIdAndProviderAndExternalId(userId, LINKEDIN, profile.sub())
                .orElse(null);
        SocialAccount target;
        if (existing != null) {
            existing.reconnectLinkedin(profile.sub(), profile.name(), profile.picture(),
                    token.accessToken(), token.refreshToken(), expiresAt);
            target = existing;
        } else {
            assertCanAddChannel(userId); // new channel → plan gate (cross-provider)
            target = repository.save(SocialAccount.connectLinkedin(
                    userId, profile.sub(), profile.name(), profile.picture(),
                    token.accessToken(), token.refreshToken(), expiresAt));
        }
        makeDefault(userId, target); // the member channel becomes the active channel

        // Best-effort: also register the organizations (company pages) the user administers as
        // channels. Needs r_organization_admin + Community Management API approval — if the token
        // lacks the scope the ACL call 403s and we simply skip org channels.
        connectAdminOrganizations(userId, token.accessToken(), token.refreshToken(), expiresAt);
    }

    /** Upsert each admin organization as a LINKEDIN channel (externalId = full org URN). */
    private void connectAdminOrganizations(Long userId, String accessToken, String refreshToken,
                                           Instant expiresAt) {
        List<String> orgUrns;
        try {
            orgUrns = client.adminOrganizationUrns(accessToken);
        } catch (LinkedInApiException e) {
            log.debug("LinkedIn org channels skipped (scope/approval): {}", e.getMessage());
            return;
        }
        boolean multi = PlanPolicy.canMultiAccount(userService.getById(userId).getPlan());
        for (String orgUrn : orgUrns) {
            SocialAccount existing = repository
                    .findByUserIdAndProviderAndExternalId(userId, LINKEDIN, orgUrn)
                    .orElse(null);
            String name = client.organizationName(orgUrn, accessToken);
            if (existing != null) {
                existing.reconnectLinkedin(orgUrn, name, null, accessToken, refreshToken, expiresAt);
                continue;
            }
            if (!multi && repository.countByUserId(userId) >= 1) {
                continue; // Free plan channel limit
            }
            repository.save(SocialAccount.connectLinkedin(
                    userId, orgUrn, name, null, accessToken, refreshToken, expiresAt));
        }
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

    private Instant expiryFrom(Long expiresInSeconds) {
        long seconds = expiresInSeconds != null ? expiresInSeconds : 0L;
        return Instant.now().plusSeconds(seconds);
    }
}
