package com.postflow.billing;

import com.postflow.user.Plan;
import com.postflow.user.User;
import com.postflow.user.UserRepository;
import com.postflow.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Server-to-server billing sync for the standalone payment site.
 * The payment site authenticates with a shared secret ({@code BILLING_SYNC_SECRET}) and pushes
 * plan changes here after a successful payment/cancellation. No user JWT (machine call).
 */
@RestController
@RequestMapping("/api/internal/billing")
public class InternalBillingController {

    private static final Logger log = LoggerFactory.getLogger(InternalBillingController.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final String secret;

    public InternalBillingController(UserService userService, UserRepository userRepository,
                                     @Value("${billing.sync.secret:}") String secret) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.secret = secret;
    }

    /** action: ACTIVATE(plan,periodEnd) | SCHEDULE_CANCEL(periodEnd) | CANCEL(즉시 FREE). user = userId 또는 email. */
    public record SyncRequest(Long userId, String email, String plan, String action, String periodEnd) {
    }

    @PostMapping("/plan")
    public ResponseEntity<?> sync(@RequestHeader(name = "X-Internal-Token", required = false) String token,
                                  @RequestBody SyncRequest req) {
        if (secret == null || secret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "sync_not_configured"));
        }
        if (token == null || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }

        Long userId = req.userId() != null ? req.userId()
                : (req.email() == null ? null
                        : userRepository.findByEmail(req.email()).map(User::getId).orElse(null));
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user_not_found"));
        }

        String action = req.action() == null ? "" : req.action().toUpperCase();
        Instant periodEnd = req.periodEnd() != null && !req.periodEnd().isBlank()
                ? Instant.parse(req.periodEnd())
                : Instant.now().plus(30, ChronoUnit.DAYS);
        try {
            switch (action) {
                case "ACTIVATE" -> userService.activateSubscription(userId, Plan.valueOf(req.plan()), null, periodEnd);
                case "SCHEDULE_CANCEL" -> userService.scheduleCancel(userId, periodEnd);
                case "CANCEL" -> userService.endSubscription(userId);
                default -> {
                    return ResponseEntity.badRequest().body(Map.of("error", "bad_action"));
                }
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "bad_plan"));
        }
        log.info("Billing sync: user {} action {} plan {}", userId, action, req.plan());
        return ResponseEntity.ok(Map.of("ok", true, "userId", userId, "action", action));
    }
}
