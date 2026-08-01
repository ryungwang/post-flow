package com.postflow.automation.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentRuleRequest(
        Long postId,
        String provider,   // 대상 SNS(THREADS/BLUESKY/…). null·빈값 = 전체 SNS.
        @NotBlank String keyword,
        @NotBlank String replyTemplate,
        Long ctaLinkId,
        Boolean active
) {
}
