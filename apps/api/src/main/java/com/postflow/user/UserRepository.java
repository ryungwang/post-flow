package com.postflow.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    java.util.List<User> findByCancelScheduledTrueAndCurrentPeriodEndBefore(java.time.Instant now);

    /** Active(취소 예약 아님) Toss 구독 중 기간이 지난 갱신 대상. */
    java.util.List<User> findByCancelScheduledFalseAndTossBillingKeyIsNotNullAndCurrentPeriodEndBefore(java.time.Instant now);
}
