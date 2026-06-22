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

    @Transactional
    public void linkStripeCustomer(Long userId, String customerId) {
        if (customerId != null) {
            getById(userId).setStripeCustomerId(customerId);
        }
    }

    /** Downgrade the user owning this Stripe customer to FREE (subscription canceled). */
    @Transactional
    public void downgradeByStripeCustomer(String customerId) {
        userRepository.findByStripeCustomerId(customerId).ifPresent(u -> u.changePlan(Plan.FREE));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }
}
