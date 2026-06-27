package com.postflow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.user.Plan;
import com.postflow.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TossBillingController.class);

    private final TossPaymentProvider toss;
    private final UserService userService;
    private final String clientKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public record PlanRequest(String plan) {
    }

    /**
     * 카드 재입력 없이 결제: 저장된 빌링키가 있으면 즉시 청구+활성화(needCard=false),
     * 없으면 카드 등록이 필요함을 알린다(needCard=true).
     */
    @PostMapping("/charge")
    public Map<String, Object> charge(@AuthenticationPrincipal Long userId, @RequestBody PlanRequest req) {
        Plan plan = Plan.valueOf(req.plan());
        String billingKey = userService.getById(userId).getTossBillingKey();
        if (billingKey == null || billingKey.isBlank()) {
            return Map.of("needCard", true);
        }
        String customerKey = "user_" + userId;
        String orderId = "sub_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8);
        toss.charge(billingKey, customerKey, toss.priceOf(plan), orderId, plan.name() + " 구독");
        userService.activateTossSubscription(userId, plan, billingKey, Instant.now().plus(30, ChronoUnit.DAYS));
        return Map.of("charged", true, "plan", plan.name());
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

    /**
     * Toss webhook(public). 서명이 없으므로 payload의 paymentKey로 결제를 <b>재조회해 검증</b>한 뒤
     * 처리한다(위·변조 방지). 결제가 취소/환불되면 orderId(sub_/renew_{userId})로 사용자를 강등.
     */
    @PostMapping("/webhook")
    public org.springframework.http.ResponseEntity<String> webhook(@RequestBody String payload) {
        try {
            JsonNode body = objectMapper.readTree(payload);
            JsonNode data = body.has("data") ? body.get("data") : body;
            String paymentKey = data.path("paymentKey").asText(null);
            if (paymentKey == null) {
                return org.springframework.http.ResponseEntity.ok("ignored");
            }
            JsonNode pay = toss.fetchPayment(paymentKey); // 재조회 = 진위 검증
            if (pay == null) {
                return org.springframework.http.ResponseEntity.ok("unverified");
            }
            String status = pay.path("status").asText("");
            String orderId = pay.path("orderId").asText("");
            if (("CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status)) && orderId.matches("(sub|renew)_\\d+_.*")) {
                Long userId = Long.valueOf(orderId.split("_")[1]);
                userService.endSubscription(userId);
                log.info("Toss webhook: payment {} {} → user {} 강등", paymentKey, status, userId);
            }
            return org.springframework.http.ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.warn("Toss webhook 처리 실패: {}", e.getMessage());
            return org.springframework.http.ResponseEntity.ok("error"); // 200으로 재전송 폭주 방지
        }
    }
}
