package com.postflow.roi;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public lead capture + CTA meta via a short-link slug (hosted landing page). */
@RestController
public class LeadController {

    private final CtaLinkRepository ctaLinkRepository;
    private final RoiService roiService;

    public LeadController(CtaLinkRepository ctaLinkRepository, RoiService roiService) {
        this.ctaLinkRepository = ctaLinkRepository;
        this.roiService = roiService;
    }

    public record LeadRequest(@NotBlank String email, String name) {
    }

    /** Landing page metadata (public). */
    @GetMapping("/public/cta-links/{slug}")
    public ResponseEntity<Map<String, Object>> meta(@PathVariable String slug) {
        return ctaLinkRepository.findBySlug(slug)
                .map(link -> ResponseEntity.ok(Map.<String, Object>of(
                        "slug", link.getSlug(),
                        "headline", link.getHeadline() != null ? link.getHeadline() : "무료 자료를 받아보세요",
                        "label", link.getLabel() != null ? link.getLabel() : "",
                        "destinationUrl", link.getDestinationUrl())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/public/leads/{slug}")
    public ResponseEntity<Map<String, Object>> capture(@PathVariable String slug,
                                                       @RequestBody LeadRequest request) {
        return ctaLinkRepository.findBySlug(slug)
                .map(link -> {
                    Lead lead = roiService.createLead(
                            link.getUserId(), link.getPostId(), link.getId(),
                            request.email(), request.name(), "cta:" + slug);
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.<String, Object>of("id", lead.getId(), "destinationUrl", link.getDestinationUrl()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
