package com.postflow.automation;

import com.postflow.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Auto-reply rule: when a comment on a post contains {@code keyword}, reply with the template. */
@Getter
@Entity
@Table(name = "comment_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** null = applies to all of the user's published posts. */
    @Column(name = "post_id")
    private Long postId;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "reply_template", nullable = false, length = 500)
    private String replyTemplate;

    /** Optional tracking link; replaces the {link} placeholder in the template. */
    @Column(name = "cta_link_id")
    private Long ctaLinkId;

    @Column(nullable = false)
    private boolean active = true;

    public static CommentRule create(Long userId, Long postId, String keyword, String replyTemplate, Long ctaLinkId) {
        CommentRule r = new CommentRule();
        r.userId = userId;
        r.postId = postId;
        r.keyword = keyword;
        r.replyTemplate = replyTemplate;
        r.ctaLinkId = ctaLinkId;
        r.active = true;
        return r;
    }

    public void update(String keyword, String replyTemplate, Long ctaLinkId, Boolean active) {
        if (keyword != null && !keyword.isBlank()) this.keyword = keyword;
        if (replyTemplate != null && !replyTemplate.isBlank()) this.replyTemplate = replyTemplate;
        this.ctaLinkId = ctaLinkId;
        if (active != null) this.active = active;
    }

    public boolean matches(String comment) {
        return comment != null && keyword != null
                && comment.toLowerCase().contains(keyword.toLowerCase());
    }
}
