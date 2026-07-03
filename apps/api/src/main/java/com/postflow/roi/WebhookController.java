package com.postflow.roi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.user.User;
import com.postflow.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * External conversion webhook (store/payment). The body is HMAC-SHA256 signed with the
 * recipient's <b>per-user</b> webhook secret (managed in account settings); the signature is in
 * the {@code X-PostFlow-Signature} header (hex, optional "sha256=" prefix). The body is parsed
 * (untrusted) only to route to a user via {@code slug}/{@code postId}; the signature is then
 * verified with that user's secret before anything is recorded.
 */
@RestController
public class WebhookController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoiService roiService;
    private final CtaLinkService ctaLinkService;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public WebhookController(RoiService roiService,
                             CtaLinkService ctaLinkService,
                             PostRepository postRepository,
                             UserRepository userRepository) {
        this.roiService = roiService;
        this.ctaLinkService = ctaLinkService;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    /** Reject signed payloads whose timestamp is older/newer than this (replay protection). */
    private static final long MAX_SKEW_SECONDS = 300;

    @PostMapping("/webhooks/conversions")
    public ResponseEntity<?> conversion(@RequestHeader(name = "X-PostFlow-Signature", required = false) String signature,
                                        @RequestHeader(name = "X-PostFlow-Timestamp", required = false) String timestamp,
                                        @RequestBody String rawBody) {
        if (!freshTimestamp(timestamp)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "stale or missing timestamp"));
        }
        Long userId;
        Long postId;
        BigDecimal amount;
        String currency;
        String note;
        try {
            JsonNode b = objectMapper.readTree(rawBody);
            if (b.hasNonNull("slug")) {
                CtaLink link = ctaLinkService.getBySlug(b.get("slug").asText());
                userId = link.getUserId();
                postId = link.getPostId();
            } else if (b.hasNonNull("postId")) {
                postId = b.get("postId").asLong();
                Post post = postRepository.findById(postId).orElseThrow();
                userId = post.getUserId();
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "slug or postId required"));
            }
            amount = new BigDecimal(b.get("amount").asText());
            currency = b.hasNonNull("currency") ? b.get("currency").asText() : "KRW";
            note = b.hasNonNull("note") ? b.get("note").asText() : null;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid payload"));
        }

        String secret = userRepository.findById(userId).map(User::getWebhookSecret).orElse(null);
        // signature covers "timestamp.body" so the timestamp can't be tampered with
        if (secret == null || secret.isBlank() || !verify(timestamp + "." + rawBody, signature, secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid signature"));
        }

        Conversion c = roiService.recordWebhookConversion(userId, postId, amount, currency, note);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", c.getId()));
    }

    private boolean freshTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }
        try {
            long ts = Long.parseLong(timestamp.trim());
            long now = java.time.Instant.now().getEpochSecond();
            return Math.abs(now - ts) <= MAX_SKEW_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean verify(String body, String signature, String secret) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String provided = signature.startsWith("sha256=") ? signature.substring(7) : signature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte x : digest) {
                hex.append(String.format("%02x", x));
            }
            return MessageDigest.isEqual(
                    hex.toString().getBytes(StandardCharsets.UTF_8),
                    provided.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
