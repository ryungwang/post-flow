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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    /** Stripe customer id (set after first checkout; used for the billing portal). */
    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

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

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    /** Active paid subscription: set plan, period end, clear any scheduled cancel. */
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

    public void resumeSubscription() {
        this.cancelScheduled = false;
    }

    /** Period ended (or hard cancel): back to FREE. */
    public void endSubscription() {
        this.plan = Plan.FREE;
        this.cancelScheduled = false;
        this.currentPeriodEnd = null;
    }
}
