package com.postflow.user;

import com.postflow.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    /** synub-sso 시드 데모 계정의 external_id. 이 유저 세션은 읽기전용(체험) — 모든 서비스 공통 규칙. */
    public static final String DEMO_EXTERNAL_ID = "demo-user";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SSO(통합계정) 신원 = 토큰 sub(external_id). 크레덴셜은 SSO에만 있고 여긴 없음. */
    @Column(name = "external_id", length = 64, unique = true)
    private String externalId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "profile_image")
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    /** Per-user HMAC secret for the conversion webhook (null until first issued). */
    @Column(name = "webhook_secret", length = 64)
    private String webhookSecret;

    // 결제/구독 상태는 synub-billing이 진실. 아래는 entitlements/웹훅으로 갱신되는 로컬 캐시.

    /** End of the current paid period; plan stays active until this instant. */
    @Column(name = "current_period_end")
    private java.time.Instant currentPeriodEnd;

    /** True when cancellation is scheduled — keep plan until currentPeriodEnd, then downgrade. */
    @Column(name = "cancel_scheduled", nullable = false)
    private boolean cancelScheduled = false;

    public static User create(String email, String name, String profileImage) {
        User u = new User();
        u.email = email;
        u.name = name;
        u.profileImage = profileImage;
        u.plan = Plan.FREE;
        return u;
    }

    /** Create a local profile linked to an SSO identity (no credentials stored here). */
    public static User createFromSso(String externalId, String email, String name) {
        User u = new User();
        u.externalId = externalId;
        u.email = email;
        u.name = name;
        u.plan = Plan.FREE;
        return u;
    }

    public void linkExternalId(String externalId) {
        this.externalId = externalId;
    }

    /** 데모(체험) 계정 여부 — SSO 데모 계정으로 로그인한 세션이면 쓰기 차단(read-only). */
    public boolean isDemo() {
        return DEMO_EXTERNAL_ID.equals(externalId);
    }

    /** Refresh mutable profile fields from the identity provider on each login. */
    public void updateProfile(String name, String profileImage) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.profileImage = profileImage;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public void changePlan(Plan plan) {
        this.plan = plan;
    }

    /** Active paid subscription: set plan, period end, clear scheduled cancel. */
    public void activateSubscription(Plan plan, java.time.Instant periodEnd) {
        this.plan = plan;
        this.currentPeriodEnd = periodEnd;
        this.cancelScheduled = false;
    }

    /** Cancellation scheduled: keep current plan until periodEnd, then it downgrades. */
    public void scheduleCancel(java.time.Instant periodEnd) {
        this.cancelScheduled = true;
        if (periodEnd != null) {
            this.currentPeriodEnd = periodEnd;
        }
    }

    /** Period ended (or hard cancel): back to FREE. */
    public void endSubscription() {
        this.plan = Plan.FREE;
        this.cancelScheduled = false;
        this.currentPeriodEnd = null;
    }
}
