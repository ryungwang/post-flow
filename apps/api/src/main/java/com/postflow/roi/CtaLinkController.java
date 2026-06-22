package com.postflow.roi;

import com.postflow.roi.dto.CreateCtaLinkRequest;
import com.postflow.roi.dto.CtaLinkDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/cta-links")
public class CtaLinkController {

    private final CtaLinkService ctaLinkService;

    public CtaLinkController(CtaLinkService ctaLinkService) {
        this.ctaLinkService = ctaLinkService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CtaLinkDto create(@AuthenticationPrincipal Long userId,
                             @PathVariable Long postId,
                             @Valid @RequestBody CreateCtaLinkRequest request) {
        CtaLink link = ctaLinkService.createLink(userId, postId, request.destinationUrl(),
                request.label(), request.captureLead(), request.headline());
        return CtaLinkDto.from(link, ctaLinkService.baseUrl());
    }
}
