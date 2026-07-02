package com.postflow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.user.Plan;
import com.postflow.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;

/**
 * Receives synub-billing subscription webhooks (push) and reconciles the local plan cache.
 * Verifies the {@code X-Synub-Signature} HMAC-SHA256 over the raw body. State updates are
 * idempotent (plan set / downgrade), so duplicate deliveries are safe.
 *
 * <p>NOTE: register this URL as the product's webhook_url in synub-billing:
 * {@code {api-domain}/api/webhooks/billing}.
 */
@RestController
@RequestMapping("/api/webhooks/billing")
public class BillingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookController.class);

    private final UserService userService;
    private final String secret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BillingWebhookController(UserService userService,
                                    @Value("${billing.webhook-secret}") String secret) {
        this.userService = userService;
        this.secret = secret;
    }

    @PostMapping
    public ResponseEntity<String> receive(@RequestHeader(name = "X-Synub-Signature", required = false) String signature,
                                          @RequestBody String payload) {
        if (!verify(payload, signature)) {
            return ResponseEntity.status(401).body("bad signature");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("");
            JsonNode data = root.path("data");
            String externalId = data.path("customerExternalId").asText(null);
            Long userId = externalId == null ? null : userService.findIdByExternalId(externalId);
            if (userId == null) {
                // 아직 post-flow에 로그인 안 한 사용자 → 첫 로그인 시 entitlements pull로 보정.
                return ResponseEntity.ok("no local user");
            }
            switch (event) {
                case "subscription.activated", "subscription.plan_changed" ->
                        userService.applyEntitlement(userId, Plan.fromBillingCode(data.path("plan").asText(null)),
                                toInstant(data.path("nextBillingDate").asText(null)));
                case "subscription.canceled" ->
                        userService.scheduleCancel(userId, toInstant(data.path("nextBillingDate").asText(null)));
                case "subscription.suspended" -> userService.endSubscription(userId);
                case "subscription.payment_failed" -> log.info("payment_failed for user {}", userId);
                default -> log.info("unhandled billing event {}", event);
            }
            log.info("Billing webhook: {} → user {}", event, userId);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.warn("Billing webhook error: {}", e.getMessage());
            return ResponseEntity.ok("error"); // 2xx로 재시도 폭주 방지(서명은 이미 검증됨)
        }
    }

    private boolean verify(String body, String signature) {
        if (signature == null || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private Instant toInstant(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
