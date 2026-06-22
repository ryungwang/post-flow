package com.postflow.automation.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentRuleRequest(
        Long postId,
        @NotBlank String keyword,
        @NotBlank String replyTemplate,
        Long ctaLinkId,
        Boolean active
) {
}
