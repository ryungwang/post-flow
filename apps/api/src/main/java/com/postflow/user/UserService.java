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

    /**
     * Resolve the local user for a verified SSO identity (token sub = external_id).
     * Create on first login; else refresh cached profile. Links a pre-existing email match once.
     */
    @Transactional
    public Long resolveBySso(String externalId, String email, String name) {
        User user = userRepository.findByExternalId(externalId)
                .or(() -> userRepository.findByEmail(email).map(existing -> {
                    existing.linkExternalId(externalId); // 기존 이메일 유저를 SSO 신원에 1회 연결
                    return existing;
                }))
                .orElseGet(() -> userRepository.save(User.createFromSso(externalId, email, name)));
        user.updateProfile(name, user.getProfileImage());
        return user.getId();
    }

    @Transactional
    public void changePlan(Long userId, Plan plan) {
        getById(userId).changePlan(plan);
    }

    // ── 구독 상태 반영 (진실은 synub-billing; entitlements/웹훅이 아래를 호출해 로컬 캐시 갱신) ──

    /** Activate a paid subscription (from billing) with its period end. */
    @Transactional
    public void activateSubscription(Long userId, Plan plan, java.time.Instant periodEnd) {
        getById(userId).activateSubscription(plan, periodEnd);
    }

    /** Schedule end-of-period cancellation; plan stays until periodEnd. */
    @Transactional
    public java.time.Instant scheduleCancel(Long userId, java.time.Instant fallbackPeriodEnd) {
        User user = getById(userId);
        java.time.Instant end = user.getCurrentPeriodEnd() != null ? user.getCurrentPeriodEnd() : fallbackPeriodEnd;
        user.scheduleCancel(end);
        return end;
    }

    /** Downgrade a specific user to FREE (subscription canceled/suspended). */
    @Transactional
    public void endSubscription(Long userId) {
        userRepository.findById(userId).ifPresent(User::endSubscription);
    }

    /** Reconcile local plan cache from a billing entitlement (FREE ⇒ end, else activate). */
    @Transactional
    public void applyEntitlement(Long userId, Plan plan, java.time.Instant periodEnd) {
        User user = getById(userId);
        if (plan == Plan.FREE) {
            user.endSubscription();
        } else {
            user.activateSubscription(plan, periodEnd);
        }
    }

    /** Resolve local user id for an SSO external id (webhook path); null if unknown. */
    @Transactional(readOnly = true)
    public Long findIdByExternalId(String externalId) {
        return userRepository.findByExternalId(externalId).map(User::getId).orElse(null);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }
}
