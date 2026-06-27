package com.postflow.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Return the user's webhook secret, generating one on first access. */
    @Transactional
    public String getOrCreateWebhookSecret(Long userId) {
        User user = getById(userId);
        if (user.getWebhookSecret() == null || user.getWebhookSecret().isBlank()) {
            user.setWebhookSecret(generateSecret());
        }
        return user.getWebhookSecret();
    }

    @Transactional
    public String regenerateWebhookSecret(Long userId) {
        User user = getById(userId);
        user.setWebhookSecret(generateSecret());
        return user.getWebhookSecret();
    }

    private String generateSecret() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Upsert a user from a verified Google identity (create on first login, else refresh profile). */
    @Transactional
    public User upsertFromGoogle(String email, String name, String profileImage) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    existing.updateProfile(name, profileImage);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.create(email, name, profileImage)));
    }

    @Transactional
    public void changePlan(Long userId, Plan plan) {
        getById(userId).changePlan(plan);
    }

    /** Toss: store billing key + activate the plan for the paid period. */
    @Transactional
    public void activateTossSubscription(Long userId, Plan plan, String billingKey, java.time.Instant periodEnd) {
        User user = getById(userId);
        user.setTossBillingKey(billingKey);
        user.activateSubscription(plan, periodEnd);
    }

    @Transactional
    public void linkStripeCustomer(Long userId, String customerId) {
        if (customerId != null) {
            getById(userId).setStripeCustomerId(customerId);
        }
    }

    /** Activate a paid subscription (upgrade) with its period end. */
    @Transactional
    public void activateSubscription(Long userId, Plan plan, String customerId, java.time.Instant periodEnd) {
        User user = getById(userId);
        user.activateSubscription(plan, periodEnd);
        if (customerId != null) {
            user.setStripeCustomerId(customerId);
        }
    }

    /** Schedule end-of-period cancellation; plan stays until periodEnd. */
    @Transactional
    public void scheduleCancelByCustomer(String customerId, java.time.Instant periodEnd) {
        userRepository.findByStripeCustomerId(customerId).ifPresent(u -> u.scheduleCancel(periodEnd));
    }

    @Transactional
    public java.time.Instant scheduleCancel(Long userId, java.time.Instant fallbackPeriodEnd) {
        User user = getById(userId);
        java.time.Instant end = user.getCurrentPeriodEnd() != null ? user.getCurrentPeriodEnd() : fallbackPeriodEnd;
        user.scheduleCancel(end);
        return end;
    }

    @Transactional
    public void resumeByCustomer(String customerId) {
        userRepository.findByStripeCustomerId(customerId).ifPresent(User::resumeSubscription);
    }

    /** Period ended → downgrade the user owning this Stripe customer to FREE. */
    @Transactional
    public void downgradeByStripeCustomer(String customerId) {
        userRepository.findByStripeCustomerId(customerId).ifPresent(User::endSubscription);
    }

    /** Downgrade a specific user to FREE (e.g., Toss payment canceled/refunded). */
    @Transactional
    public void endSubscription(Long userId) {
        userRepository.findById(userId).ifPresent(User::endSubscription);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }
}
