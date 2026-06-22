package com.postflow.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    java.util.List<User> findByCancelScheduledTrueAndCurrentPeriodEndBefore(java.time.Instant now);
}
