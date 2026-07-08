package com.postflow.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    private UserService self; // 프록시 경유 REQUIRES_NEW 호출용(self-invocation은 트랜잭션 무시되므로)

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired
    public void setSelf(@Lazy UserService self) {
        this.self = self;
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
                .orElse(null);
        if (user == null) {
            // 첫 로그인 생성. JwtAuthenticationFilter가 매 요청마다 호출하므로 로그인 직후
            // 대시보드의 동시 요청들이 각자 insert → users_email/external_id 유니크 충돌(500).
            // 별도 트랜잭션으로 생성 시도하고, 충돌 시 승자가 만든 행을 재조회한다(get-or-create 레이스 방어).
            try {
                return self.createFromSsoIsolated(externalId, email, name);
            } catch (DataIntegrityViolationException race) {
                return userRepository.findByExternalId(externalId)
                        .map(User::getId)
                        .orElseThrow(() -> race); // 충돌인데 행도 없으면 진짜 이상 → 원예외 전파
            }
        }
        user.updateProfile(name, user.getProfileImage());
        return user.getId();
    }

    /**
     * 신규 SSO 유저 생성을 별도(REQUIRES_NEW) 트랜잭션으로 격리한다. 유니크 충돌 시 이 내부
     * 트랜잭션만 롤백되고 호출측(외부 트랜잭션)은 오염되지 않아, 호출측이 승자 행을 재조회할 수 있다.
     * saveAndFlush로 insert를 즉시 실행해 충돌을 여기서(외부 커밋이 아니라) 발생시킨다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createFromSsoIsolated(String externalId, String email, String name) {
        return userRepository.saveAndFlush(User.createFromSso(externalId, email, name)).getId();
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
