package com.postflow.automation;

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

import java.time.Instant;

/** Log of an auto-reply already sent, to avoid replying to the same comment twice. */
@Getter
@Entity
@Table(name = "comment_replies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "post_id")
    private Long postId;

    /** 어느 플랫폼의 댓글인지 — 플랫폼 간 id 충돌 방지를 위해 중복 방지 키에 포함된다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "comment_id", nullable = false, length = 64)
    private String commentId;

    @Column(name = "replied_at", nullable = false)
    private Instant repliedAt;

    public static CommentReply of(Long ruleId, Long postId, SocialProvider provider, String commentId) {
        CommentReply r = new CommentReply();
        r.ruleId = ruleId;
        r.postId = postId;
        r.provider = provider;
        r.commentId = commentId;
        r.repliedAt = Instant.now();
        return r;
    }
}
