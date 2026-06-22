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

    /**
     * Verify + handle a provider webhook. Returns the (userId, plan) to upgrade, or null if the
     * event isn't a completed purchase.
     */
    PlanChange handleWebhook(String payload, String signature);

    record PlanChange(Long userId, Plan plan) {
    }
}
