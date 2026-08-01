package com.postflow.automation;

import com.postflow.common.entity.BaseTimeEntity;
import com.postflow.social.SocialProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 대상 SNS. null = 전체 SNS. 지정 시 그 플랫폼 채널에만 답글을 단다(한 글이 여러 SNS로 팬아웃돼도). */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SocialProvider provider;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "reply_template", nullable = false, length = 500)
    private String replyTemplate;

    /** Optional tracking link; replaces the {link} placeholder in the template. */
    @Column(name = "cta_link_id")
    private Long ctaLinkId;

    @Column(nullable = false)
    private boolean active = true;

    public static CommentRule create(Long userId, Long postId, SocialProvider provider,
                                     String keyword, String replyTemplate, Long ctaLinkId) {
        CommentRule r = new CommentRule();
        r.userId = userId;
        r.postId = postId;
        r.provider = provider;
        r.keyword = keyword;
        r.replyTemplate = replyTemplate;
        r.ctaLinkId = ctaLinkId;
        r.active = true;
        return r;
    }

    public void update(String keyword, String replyTemplate, Long ctaLinkId, Boolean active) {
        // 대상(postId·provider)은 생성 시에만 정한다 — 토글 등 부분 수정에서 초기화되지 않게 여기선 안 건드림.
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
