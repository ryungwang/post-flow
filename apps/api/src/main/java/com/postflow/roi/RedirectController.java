package com.postflow.roi;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Public short-link redirect: records the click, then 302s to the destination. */
@RestController
public class RedirectController {

    private final CtaLinkService ctaLinkService;

    public RedirectController(CtaLinkService ctaLinkService) {
        this.ctaLinkService = ctaLinkService;
    }

    @GetMapping("/r/{slug}")
    public ResponseEntity<Void> redirect(@PathVariable String slug,
                                         @RequestHeader(name = "Referer", required = false) String referrer,
                                         @RequestHeader(name = "User-Agent", required = false) String ua,
                                         HttpServletRequest request) {
        try {
            String destination = ctaLinkService.resolveAndRecordClick(slug, referrer, ua, clientIp(request));
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination)).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
