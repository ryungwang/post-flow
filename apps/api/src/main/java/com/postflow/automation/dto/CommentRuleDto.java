package com.postflow.automation.dto;

import com.postflow.automation.CommentRule;

public record CommentRuleDto(
        Long id,
        Long postId,
        String keyword,
        String replyTemplate,
        Long ctaLinkId,
        boolean active
) {
    public static CommentRuleDto from(CommentRule r) {
        return new CommentRuleDto(r.getId(), r.getPostId(), r.getKeyword(),
                r.getReplyTemplate(), r.getCtaLinkId(), r.isActive());
    }
}
