package com.postflow.billing;

import com.postflow.user.User;
import com.postflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Safety net for end-of-period downgrades: if a subscription was canceled (cancelScheduled)
 * and its period has ended, downgrade to FREE — even if the Stripe webhook was missed, and
 * for the dev/local path where there is no webhook at all.
 */
@Component
public class SubscriptionReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconciliationJob.class);

    private final UserRepository userRepository;

    public SubscriptionReconciliationJob(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Hourly: downgrade users whose canceled subscription period has ended. */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void downgradeExpired() {
        List<User> expired = userRepository.findByCancelScheduledTrueAndCurrentPeriodEndBefore(Instant.now());
        for (User u : expired) {
            u.endSubscription();
            log.info("Subscription period ended → downgraded user {} to FREE", u.getId());
        }
    }
}
