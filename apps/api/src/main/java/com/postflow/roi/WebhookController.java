package com.postflow.roi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import org.springframework.beans.factory.annotation.Value;
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
 * External conversion webhook (store/payment). Body is HMAC-SHA256 signed with the shared
 * secret; signature in the {@code X-PostFlow-Signature} header (hex, optional "sha256=" prefix).
 * Attribution: {@code slug} (preferred) or {@code postId}; userId is derived, never trusted from body.
 */
@RestController
public class WebhookController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String secret;
    private final RoiService roiService;
    private final CtaLinkService ctaLinkService;
    private final PostRepository postRepository;

    public WebhookController(@Value("${roi.webhook-secret:dev-webhook-secret-change-me}") String secret,
                             RoiService roiService,
                             CtaLinkService ctaLinkService,
                             PostRepository postRepository) {
        this.secret = secret;
        this.roiService = roiService;
        this.ctaLinkService = ctaLinkService;
        this.postRepository = postRepository;
    }

    @PostMapping("/api/webhooks/conversions")
    public ResponseEntity<?> conversion(@RequestHeader(name = "X-PostFlow-Signature", required = false) String signature,
                                        @RequestBody String rawBody) {
        if (!verify(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid signature"));
        }
        try {
            JsonNode b = objectMapper.readTree(rawBody);
            Long userId;
            Long postId;
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
            BigDecimal amount = new BigDecimal(b.get("amount").asText());
            String currency = b.hasNonNull("currency") ? b.get("currency").asText() : "KRW";
            String note = b.hasNonNull("note") ? b.get("note").asText() : null;
            Conversion c = roiService.recordWebhookConversion(userId, postId, amount, currency, note);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", c.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid payload"));
        }
    }

    private boolean verify(String body, String signature) {
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
