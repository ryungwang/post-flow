package com.postflow.billing;

import com.postflow.user.Plan;

/**
 * Vendor-neutral payment abstraction (like LLMProvider / StorageService). Stripe is the
 * default impl; Toss 등 다른 PG는 이 인터페이스 구현으로 드롭인 추가.
 */
public interface PaymentProvider {

    /** Whether real payment is configured (keys present). */
    boolean isConfigured();

    /** Create a hosted checkout/subscription session; returns the redirect URL. */
    String createCheckoutUrl(Long userId, Plan plan, String successUrl, String cancelUrl);

    /** Create a hosted billing-portal session for managing/canceling the subscription. */
    String createPortalUrl(String customerId, String returnUrl);

    /** Verify + handle a provider webhook. Returns what to apply, or null if the event is ignored. */
    WebhookResult handleWebhook(String payload, String signature);

    enum Action { UPGRADE, SCHEDULE_CANCEL, RESUME, CANCEL }

    /**
     * eventId is the provider event id (for idempotency). UPGRADE: (userId, plan, customerId,
     * periodEnd). SCHEDULE_CANCEL: (customerId, periodEnd). RESUME / CANCEL: (customerId).
     */
    record WebhookResult(String eventId, Action action, Long userId, Plan plan,
                         String customerId, java.time.Instant periodEnd) {
    }
}
