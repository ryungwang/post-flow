package com.postflow.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "threads_reply_id", nullable = false, length = 64)
    private String threadsReplyId;

    @Column(name = "replied_at", nullable = false)
    private Instant repliedAt;

    public static CommentReply of(Long ruleId, Long postId, String threadsReplyId) {
        CommentReply r = new CommentReply();
        r.ruleId = ruleId;
        r.postId = postId;
        r.threadsReplyId = threadsReplyId;
        r.repliedAt = Instant.now();
        return r;
    }
}
