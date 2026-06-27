package com.postflow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.postflow.user.Plan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Toss Payments(빌링/정기결제) 구현 골격. {@code payment.provider=toss} 일 때 활성화된다.
 *
 * <p>토스 빌링 흐름(Stripe Checkout과 다름):
 * <ol>
 *   <li>프론트에서 토스 SDK {@code requestBillingAuth(customerKey, successUrl, failUrl)}로 카드 등록</li>
 *   <li>successUrl 콜백의 {@code authKey}를 서버가 받아 {@link #issueBillingKey}로 빌링키 발급</li>
 *   <li>빌링키를 사용자에 저장 → {@link #charge}로 즉시/정기 청구</li>
 *   <li>다음 결제일에 스케줄러가 {@link #charge} 반복 호출(정기결제)</li>
 * </ol>
 *
 * <p>⚠️ 골격이라 빌링키 저장/스케줄러/웹훅 검증은 TODO로 표시. 키(TOSS_SECRET_KEY)만 넣으면
 * issueBillingKey/charge는 실제 토스 API를 호출한다. 활성화 시 작은 TossBillingController
 * (authKey 수신 → 빌링키 발급 → 첫 청구 → 플랜 활성화)를 추가하면 된다.
 */
@Service
@ConditionalOnProperty(name = "payment.provider", havingValue = "toss")
public class TossPaymentProvider implements PaymentProvider {

    private static final String API_BASE = "https://api.tosspayments.com";

    private final String secretKey;
    private final Map<Plan, Long> prices; // 플랜 → 월 금액(KRW)
    private final String frontendBase;
    private final RestClient toss;

    public TossPaymentProvider(
            @Value("${toss.secret-key:}") String secretKey,
            @Value("${toss.price.starter:9900}") long priceStarter,
            @Value("${toss.price.pro:29000}") long pricePro,
            @Value("${toss.price.business:49000}") long priceBusiness,
            @Value("${roi.frontend-base-url:http://localhost:5173}") String frontendBase) {
        this.secretKey = secretKey;
        this.prices = Map.of(Plan.STARTER, priceStarter, Plan.PRO, pricePro, Plan.BUSINESS, priceBusiness);
        this.frontendBase = frontendBase;
        this.toss = RestClient.builder().baseUrl(API_BASE).build();
    }

    @Override
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    /**
     * 토스 빌링은 서버가 호스팅 URL을 만들지 않고, 프론트 SDK로 카드 등록을 시작한다.
     * 따라서 카드 등록을 진행할 프론트 결제 페이지 경로를 돌려준다(쿼리로 plan 전달).
     */
    @Override
    public String createCheckoutUrl(Long userId, Plan plan, String successUrl, String cancelUrl) {
        long amount = prices.getOrDefault(plan, 0L);
        return frontendBase + "/billing/toss?plan=" + plan.name() + "&amount=" + amount;
        // 프론트: @tosspayments/payment-sdk → requestBillingAuth(customerKey=userId, successUrl, failUrl)
    }

    /** 토스는 호스팅 구독관리 포털이 없음 → 자체 계정 화면에서 취소 처리. */
    @Override
    public String createPortalUrl(String customerId, String returnUrl) {
        return frontendBase + "/settings/account";
    }

    /**
     * 토스 웹훅(결제승인/빌링 등). 서명 헤더 검증 또는 paymentKey 재조회로 검증 후 매핑.
     * 골격: 활성화 시 이벤트 포맷에 맞춰 구현.
     */
    @Override
    public WebhookResult handleWebhook(String payload, String signature) {
        // TODO(toss): 이벤트 타입 파싱 + 검증(re-fetch by paymentKey) → WebhookResult 매핑
        return null;
    }

    // ── 토스 빌링 전용 헬퍼 (인터페이스 외, 활성화 시 TossBillingController에서 호출) ──

    /** 카드 등록 authKey → 빌링키 발급. customerKey는 사용자 식별자(예: "user_{id}"). */
    public String issueBillingKey(String authKey, String customerKey) {
        JsonNode res = toss.post()
                .uri("/v1/billing/authorizations/issue")
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .body(Map.of("authKey", authKey, "customerKey", customerKey))
                .retrieve()
                .body(JsonNode.class);
        return res != null && res.hasNonNull("billingKey") ? res.get("billingKey").asText() : null;
    }

    /** 빌링키로 즉시/정기 청구. orderId는 멱등키 역할(중복 청구 방지). */
    public JsonNode charge(String billingKey, String customerKey, long amount, String orderId, String orderName) {
        return toss.post()
                .uri("/v1/billing/{billingKey}", billingKey)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .body(Map.of(
                        "customerKey", customerKey,
                        "amount", amount,
                        "orderId", orderId,
                        "orderName", orderName))
                .retrieve()
                .body(JsonNode.class);
    }

    public long priceOf(Plan plan) {
        return prices.getOrDefault(plan, 0L);
    }

    /** Authoritative payment lookup by paymentKey (used to verify webhooks). Null if not found. */
    public JsonNode fetchPayment(String paymentKey) {
        try {
            return toss.get()
                    .uri("/v1/payments/{key}", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String basicAuth() {
        // 토스 인증: "{secretKey}:" 를 base64 (비밀번호 없음)
        String token = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
