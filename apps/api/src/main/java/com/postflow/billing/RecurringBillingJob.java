package com.postflow.billing;

import com.postflow.user.Plan;
import com.postflow.user.User;
import com.postflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Toss 정기결제 갱신: 기간이 지난 활성 구독을 저장된 빌링키로 재청구하고 기간을 연장한다.
 * (Stripe는 구독으로 자체 갱신하므로 이 잡은 payment.provider=toss 일 때만 동작.)
 * 취소 예약(cancel_scheduled) 사용자는 제외되며, 그들은 SubscriptionReconciliationJob이 기간 말 강등.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "toss")
public class RecurringBillingJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringBillingJob.class);

    private final UserRepository userRepository;
    private final TossPaymentProvider toss;

    public RecurringBillingJob(UserRepository userRepository, TossPaymentProvider toss) {
        this.userRepository = userRepository;
        this.toss = toss;
    }

    /** 매시간 갱신 대상 점검(기간 지난 활성 Toss 구독 → 재청구 + 30일 연장). */
    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void renew() {
        List<User> due = userRepository
                .findByCancelScheduledFalseAndTossBillingKeyIsNotNullAndCurrentPeriodEndBefore(Instant.now());
        for (User u : due) {
            if (u.getPlan() == Plan.FREE) {
                continue;
            }
            try {
                String customerKey = "user_" + u.getId();
                String orderId = "renew_" + u.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
                toss.charge(u.getTossBillingKey(), customerKey, toss.priceOf(u.getPlan()),
                        orderId, u.getPlan().name() + " 구독 갱신");
                u.activateSubscription(u.getPlan(), Instant.now().plus(30, ChronoUnit.DAYS));
                log.info("Recurring charge OK: user {} plan {}", u.getId(), u.getPlan());
            } catch (Exception e) {
                // TODO: 재시도/실패 알림(연속 실패 시 강등). 우선 다음 주기에 재시도되도록 기간 유지.
                log.warn("Recurring charge FAILED: user {} plan {} — {}", u.getId(), u.getPlan(), e.getMessage());
            }
        }
    }
}
