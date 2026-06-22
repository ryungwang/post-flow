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

    /**
     * Resolve a slug, record the click, and return the redirect target.
     * For lead-capture links the target is the hosted landing page; otherwise the destination.
     */
    @Transactional
    public String resolveAndRecordClick(String slug, String referrer, String ua, String ip) {
        CtaLink link = getBySlug(slug);
        linkClickRepository.save(LinkClick.of(link.getId(), link.getPostId(), referrer, ua, hash(ip)));
        return link.isCaptureLead()
                ? frontendBaseUrl + "/lp/" + slug
                : link.getDestinationUrl();
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
