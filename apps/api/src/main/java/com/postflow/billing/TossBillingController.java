package com.postflow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.postflow.user.Plan;
import com.postflow.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/** Toss billing endpoints — active only when payment.provider=toss. */
@RestController
@RequestMapping("/api/billing/toss")
@ConditionalOnProperty(name = "payment.provider", havingValue = "toss")
public class TossBillingController {

    private final TossPaymentProvider toss;
    private final UserService userService;
    private final String clientKey;

    public TossBillingController(TossPaymentProvider toss, UserService userService,
                                @Value("${toss.client-key:}") String clientKey) {
        this.toss = toss;
        this.userService = userService;
        this.clientKey = clientKey;
    }

    /**
     * Frontend needs the client key + the SAME customerKey the server will use on confirm,
     * so the billing-auth customerKey matches the charge customerKey (avoids NOT_MATCHES_CUSTOMER_KEY).
     */
    @GetMapping("/config")
    public Map<String, String> config(@AuthenticationPrincipal Long userId) {
        return Map.of("clientKey", clientKey, "customerKey", "user_" + userId);
    }

    public record ConfirmRequest(String authKey, String plan) {
    }

    /**
     * Card registered on the client → authKey here. Issue a billing key, charge the first
     * period, and activate the plan. customerKey is stable per user ("user_{id}").
     */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@AuthenticationPrincipal Long userId, @RequestBody ConfirmRequest req) {
        Plan plan = Plan.valueOf(req.plan());
        String customerKey = "user_" + userId;

        String billingKey = toss.issueBillingKey(req.authKey(), customerKey);
        if (billingKey == null) {
            throw new IllegalStateException("빌링키 발급에 실패했어요. 다시 시도해 주세요.");
        }

        long amount = toss.priceOf(plan);
        String orderId = "sub_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode charge = toss.charge(billingKey, customerKey, amount, orderId, plan.name() + " 구독");

        userService.activateTossSubscription(userId, plan, billingKey, Instant.now().plus(30, ChronoUnit.DAYS));
        return Map.of("ok", true, "plan", plan.name(),
                "status", charge != null && charge.hasNonNull("status") ? charge.get("status").asText() : "DONE");
    }
}
