package com.postflow.roi;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public lead capture via a CTA short-link slug. */
@RestController
@RequestMapping("/api/public/leads")
public class LeadController {

    private final CtaLinkRepository ctaLinkRepository;
    private final RoiService roiService;

    public LeadController(CtaLinkRepository ctaLinkRepository, RoiService roiService) {
        this.ctaLinkRepository = ctaLinkRepository;
        this.roiService = roiService;
    }

    public record LeadRequest(@NotBlank String email, String name) {
    }

    @PostMapping("/{slug}")
    public ResponseEntity<Map<String, Object>> capture(@PathVariable String slug,
                                                       @RequestBody LeadRequest request) {
        return ctaLinkRepository.findBySlug(slug)
                .map(link -> {
                    Lead lead = roiService.createLead(
                            link.getUserId(), link.getPostId(), link.getId(),
                            request.email(), request.name(), "cta:" + slug);
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.<String, Object>of("id", lead.getId()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
