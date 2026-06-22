package com.postflow.roi;

import com.postflow.post.Post;
import com.postflow.post.PostNotFoundException;
import com.postflow.post.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Service
public class CtaLinkService {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SLUG_LEN = 7;
    private final SecureRandom random = new SecureRandom();

    private final CtaLinkRepository ctaLinkRepository;
    private final LinkClickRepository linkClickRepository;
    private final PostRepository postRepository;
    private final String baseUrl;
    private final String frontendBaseUrl;

    public CtaLinkService(CtaLinkRepository ctaLinkRepository,
                          LinkClickRepository linkClickRepository,
                          PostRepository postRepository,
                          @Value("${roi.short-link-base-url:http://localhost:8080}") String baseUrl,
                          @Value("${roi.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.ctaLinkRepository = ctaLinkRepository;
        this.linkClickRepository = linkClickRepository;
        this.postRepository = postRepository;
        this.baseUrl = baseUrl;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    @Transactional
    public CtaLink createLink(Long userId, Long postId, String destinationUrl, String label,
                              boolean captureLead, String headline) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.getUserId().equals(userId)) {
            throw new PostNotFoundException(postId);
        }
        if (!isHttpUrl(destinationUrl)) {
            throw new IllegalArgumentException("destinationUrl must be an http(s) URL");
        }
        return ctaLinkRepository.save(CtaLink.create(
                postId, userId, uniqueSlug(), destinationUrl, label, captureLead, headline));
    }

    public CtaLink getBySlug(String slug) {
        return ctaLinkRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Unknown link"));
    }

    @Transactional(readOnly = true)
    public java.util.List<com.postflow.roi.dto.CtaLinkDto> list(Long userId) {
        return ctaLinkRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(l -> com.postflow.roi.dto.CtaLinkDto.from(l, baseUrl))
                .toList();
    }

    private static final java.util.regex.Pattern BOT_UA = java.util.regex.Pattern.compile(
            "bot|crawl|spider|slurp|preview|facebookexternalhit|embedly|quora|outbrain|pinterest|"
                    + "whatsapp|telegram|twitterbot|discord|slack|headless|curl|wget|python-requests|"
                    + "go-http-client|okhttp|java/|ahrefs|semrush",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Don't count a second click from the same hashed IP for the same link within this window. */
    private static final java.time.Duration DEDUP_WINDOW = java.time.Duration.ofMinutes(30);

    /**
     * Resolve a slug, record the click (unless bot or recent duplicate), and return the redirect
     * target. For lead-capture links the target is the hosted landing page; otherwise the
     * destination (which must be an http(s) URL).
     */
    @Transactional
    public String resolveAndRecordClick(String slug, String referrer, String ua, String ip) {
        CtaLink link = getBySlug(slug);

        String ipHash = hash(ip);
        boolean bot = ua == null || ua.isBlank() || BOT_UA.matcher(ua).find();
        boolean duplicate = ipHash != null && linkClickRepository
                .existsByCtaLinkIdAndIpHashAndClickedAtAfter(link.getId(), ipHash, java.time.Instant.now().minus(DEDUP_WINDOW));
        if (!bot && !duplicate) {
            linkClickRepository.save(LinkClick.of(link.getId(), link.getPostId(), referrer, ua, ipHash));
        }

        if (link.isCaptureLead()) {
            return frontendBaseUrl + "/lp/" + slug;
        }
        // redirect-safety: only ever 302 to an http(s) destination
        return isHttpUrl(link.getDestinationUrl()) ? link.getDestinationUrl() : frontendBaseUrl;
    }

    private boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String uniqueSlug() {
        String slug;
        do {
            StringBuilder sb = new StringBuilder(SLUG_LEN);
            for (int i = 0; i < SLUG_LEN; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            slug = sb.toString();
        } while (ctaLinkRepository.existsBySlug(slug));
        return slug;
    }

    private String hash(String ip) {
        if (ip == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", d[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
