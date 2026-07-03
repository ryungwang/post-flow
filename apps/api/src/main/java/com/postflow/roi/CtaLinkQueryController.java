package com.postflow.roi;

import com.postflow.roi.dto.CtaLinkDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Flat listing of the current user's tracking links (for pickers). */
@RestController
@RequestMapping("/cta-links")
public class CtaLinkQueryController {

    private final CtaLinkService ctaLinkService;

    public CtaLinkQueryController(CtaLinkService ctaLinkService) {
        this.ctaLinkService = ctaLinkService;
    }

    @GetMapping
    public List<CtaLinkDto> list(@AuthenticationPrincipal Long userId) {
        return ctaLinkService.list(userId);
    }
}
