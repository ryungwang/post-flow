package com.postflow.billing;

import com.postflow.user.Plan;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Stripe Checkout (subscription) + Billing Portal implementation. Drop-in: fill STRIPE_* env
 * to enable real billing. Without a secret key it reports {@code isConfigured()=false} so the
 * app boots and the UI can fall back (dev upgrade/cancel) — same pattern as ClaudeProvider.
 */
@Service
public class StripePaymentProvider implements PaymentProvider {

    private final String secretKey;
    private final String webhookSecret;
    private final Map<Plan, String> priceIds;

    public StripePaymentProvider(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${stripe.price.starter:}") String priceStarter,
            @Value("${stripe.price.pro:}") String pricePro,
            @Value("${stripe.price.business:}") String priceBusiness) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.priceIds = Map.of(
                Plan.STARTER, priceStarter,
                Plan.PRO, pricePro,
                Plan.BUSINESS, priceBusiness);
        if (isConfigured()) {
            Stripe.apiKey = secretKey;
        }
    }

    @Override
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String createCheckoutUrl(Long userId, Plan plan, String successUrl, String cancelUrl) {
        String priceId = priceIds.get(plan);
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalStateException("Stripe price id not configured for " + plan);
        }
        try {
            var params = com.stripe.param.checkout.SessionCreateParams.builder()
                    .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(String.valueOf(userId))
                    .putMetadata("userId", String.valueOf(userId))
                    .putMetadata("plan", plan.name())
                    .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                            .setPrice(priceId).setQuantity(1L).build())
                    .build();
            return com.stripe.model.checkout.Session.create(params).getUrl();
        } catch (Exception e) {
            throw new IllegalStateException("Stripe checkout failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String createPortalUrl(String customerId, String returnUrl) {
        try {
            var params = com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(returnUrl)
                    .build();
            return com.stripe.model.billingportal.Session.create(params).getUrl();
        } catch (Exception e) {
            throw new IllegalStateException("Stripe portal failed: " + e.getMessage(), e);
        }
    }

    @Override
    public WebhookResult handleWebhook(String payload, String signature) {
        try {
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            String eventId = event.getId();
            return switch (event.getType()) {
                case "checkout.session.completed" -> onCheckoutCompleted(eventId, event);
                case "customer.subscription.updated" -> onSubscriptionUpdated(eventId, event);
                case "customer.subscription.deleted" -> onSubscriptionDeleted(eventId, event);
                default -> null;
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Stripe webhook", e);
        }
    }

    private WebhookResult onCheckoutCompleted(String eventId, Event event) {
        com.stripe.model.checkout.Session session =
                (com.stripe.model.checkout.Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null || session.getMetadata() == null) {
            return null;
        }
        String userId = session.getMetadata().get("userId");
        String plan = session.getMetadata().get("plan");
        if (userId == null || plan == null) {
            return null;
        }
        return new WebhookResult(eventId, Action.UPGRADE, Long.valueOf(userId), Plan.valueOf(plan),
                session.getCustomer(), periodEndOf(session.getSubscription()));
    }

    /** Cancel scheduled (cancel_at_period_end) or resumed. */
    private WebhookResult onSubscriptionUpdated(String eventId, Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (sub == null || sub.getCustomer() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(sub.getCancelAtPeriodEnd())) {
            return new WebhookResult(eventId, Action.SCHEDULE_CANCEL, null, null, sub.getCustomer(), epoch(sub.getCurrentPeriodEnd()));
        }
        return new WebhookResult(eventId, Action.RESUME, null, null, sub.getCustomer(), null);
    }

    private WebhookResult onSubscriptionDeleted(String eventId, Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (sub == null || sub.getCustomer() == null) {
            return null;
        }
        return new WebhookResult(eventId, Action.CANCEL, null, null, sub.getCustomer(), null);
    }

    private java.time.Instant periodEndOf(String subscriptionId) {
        if (subscriptionId == null) {
            return null;
        }
        try {
            return epoch(Subscription.retrieve(subscriptionId).getCurrentPeriodEnd());
        } catch (Exception e) {
            return null;
        }
    }

    private java.time.Instant epoch(Long seconds) {
        return seconds == null ? null : java.time.Instant.ofEpochSecond(seconds);
    }
}
