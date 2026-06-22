package com.postflow.billing;

import com.postflow.user.Plan;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Stripe Checkout (subscription) implementation. Drop-in: fill STRIPE_* env to enable real
 * billing. Without a secret key it reports {@code isConfigured()=false} so the app boots and
 * the UI can fall back (dev upgrade) — same pattern as ClaudeProvider.
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
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(String.valueOf(userId))
                    .putMetadata("userId", String.valueOf(userId))
                    .putMetadata("plan", plan.name())
                    .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                    .build();
            return Session.create(params).getUrl();
        } catch (Exception e) {
            throw new IllegalStateException("Stripe checkout failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PlanChange handleWebhook(String payload, String signature) {
        try {
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            if (!"checkout.session.completed".equals(event.getType())) {
                return null;
            }
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            if (session == null || session.getMetadata() == null) {
                return null;
            }
            String userId = session.getMetadata().get("userId");
            String plan = session.getMetadata().get("plan");
            if (userId == null || plan == null) {
                return null;
            }
            return new PlanChange(Long.valueOf(userId), Plan.valueOf(plan));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Stripe webhook", e);
        }
    }
}
