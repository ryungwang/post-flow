package com.postflow.threads;

import com.postflow.social.ConnectionStatus;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Proactively renews Threads long-lived tokens before they expire. Threads tokens last
 * 60 days, can be refreshed only once they are ≥24h old, and expire permanently if not
 * refreshed in time — so this job is essential for uninterrupted auto-publishing.
 */
@Component
public class TokenRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshJob.class);

    /** Refresh when the token expires within this window. */
    private static final Duration REFRESH_BEFORE = Duration.ofDays(7);
    /** Threads requires the token be at least this old before it can be refreshed. */
    private static final Duration MIN_TOKEN_AGE = Duration.ofHours(24);

    private final SocialAccountRepository repository;
    private final SocialAccountService socialAccountService;

    public TokenRefreshJob(SocialAccountRepository repository,
                           SocialAccountService socialAccountService) {
        this.repository = repository;
        this.socialAccountService = socialAccountService;
    }

    // Daily at 03:00 (server TZ). Override via threads.token-refresh.cron.
    @Scheduled(cron = "${threads.token-refresh.cron:0 0 3 * * *}")
    public void refreshExpiring() {
        Instant now = Instant.now();
        List<SocialAccount> candidates = repository.findByStatusAndExpiresAtBefore(
                ConnectionStatus.CONNECTED, now.plus(REFRESH_BEFORE));
        int refreshed = 0;
        for (SocialAccount account : candidates) {
            if (!isRefreshable(account, now)) {
                continue;
            }
            socialAccountService.refresh(account);
            refreshed++;
        }
        if (refreshed > 0) {
            log.info("Refreshed {} Threads token(s)", refreshed);
        }
    }

    private boolean isRefreshable(SocialAccount account, Instant now) {
        Instant refreshedAt = account.getLastRefreshedAt();
        return refreshedAt == null || refreshedAt.isBefore(now.minus(MIN_TOKEN_AGE));
    }
}
