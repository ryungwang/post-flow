package com.postflow.billing;

import com.postflow.user.Plan;
import com.postflow.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final PaymentProvider payment;
    private final UserService userService;
    private final ProcessedStripeEventRepository processedEvents;
    private final String frontendBase;

    public BillingController(PaymentProvider payment, UserService userService,
                            ProcessedStripeEventRepository processedEvents,
                            @Value("${roi.frontend-base-url:http://localhost:5173}") String frontendBase) {
        this.payment = payment;
        this.userService = userService;
        this.processedEvents = processedEvents;
        this.frontendBase = frontendBase;
    }

    public record CheckoutRequest(String plan) {
    }

    /**
     * Start an upgrade. With Stripe configured → returns a checkout URL to redirect to.
     * Without Stripe (dev/local) → upgrades the plan directly so it's testable, returns upgraded=true.
     */
    @PostMapping("/checkout")
    public Map<String, Object> checkout(@AuthenticationPrincipal Long userId, @RequestBody CheckoutRequest req) {
        Plan plan = Plan.valueOf(req.plan());
        if (payment.isConfigured()) {
            String url = payment.createCheckoutUrl(userId, plan,
                    frontendBase + "/settings/account?upgraded=1",
                    frontendBase + "/settings/account?canceled=1");
            return Map.of("url", url);
        }
        // not configured → dev/local convenience upgrade (30일 기간 부여)
        userService.activateSubscription(userId, plan, null, java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS));
        return Map.of("upgraded", true, "plan", plan.name());
    }

    /**
     * Manage/cancel subscription. With Stripe → returns the billing-portal URL (user cancels there).
     * Without Stripe (dev/local) → cancels directly by downgrading to FREE.
     */
    @PostMapping("/portal")
    public Map<String, Object> portal(@AuthenticationPrincipal Long userId) {
        if (payment.isConfigured()) {
            String customerId = userService.getById(userId).getStripeCustomerId();
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalStateException("결제 내역이 없어요.");
            }
            String url = payment.createPortalUrl(customerId, frontendBase + "/settings/account");
            return Map.of("url", url);
        }
        // dev/local: 즉시 강등이 아니라 기간 말 취소 예약(결제한 기간은 유지)
        java.time.Instant end = userService.scheduleCancel(userId,
                java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS));
        return Map.of("scheduled", true, "periodEnd", end.toString());
    }

    /** Stripe webhook (public). Upgrades on checkout, downgrades on subscription cancellation. */
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestHeader(name = "Stripe-Signature", required = false) String signature,
                                          @RequestBody String payload) {
        PaymentProvider.WebhookResult result = payment.handleWebhook(payload, signature);
        if (result != null) {
            // idempotency: skip events we've already applied (Stripe retries / out-of-order delivery)
            if (result.eventId() != null && processedEvents.existsById(result.eventId())) {
                return ResponseEntity.ok("duplicate");
            }
            switch (result.action()) {
                case UPGRADE -> userService.activateSubscription(
                        result.userId(), result.plan(), result.customerId(), result.periodEnd());
                case SCHEDULE_CANCEL -> userService.scheduleCancelByCustomer(result.customerId(), result.periodEnd());
                case RESUME -> userService.resumeByCustomer(result.customerId());
                case CANCEL -> userService.downgradeByStripeCustomer(result.customerId());
            }
            if (result.eventId() != null) {
                try {
                    processedEvents.save(new ProcessedStripeEvent(result.eventId(), java.time.Instant.now()));
                } catch (org.springframework.dao.DataIntegrityViolationException race) {
                    // concurrent delivery already recorded it — fine
                }
            }
        }
        return ResponseEntity.ok("ok");
    }

    /** Explicit local-only plan switch for testing gating without payment. */
    @Profile("local")
    @PostMapping("/dev-upgrade")
    public Map<String, String> devUpgrade(@AuthenticationPrincipal Long userId, @RequestBody CheckoutRequest req) {
        Plan plan = Plan.valueOf(req.plan());
        userService.changePlan(userId, plan);
        return Map.of("plan", plan.name());
    }
}
