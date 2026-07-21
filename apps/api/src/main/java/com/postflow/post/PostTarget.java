package com.postflow.post;

import com.postflow.common.entity.BaseTimeEntity;
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

/**
 * One publish destination of a {@link Post}: a specific connected channel (SocialAccount).
 * A post fans out to all its targets; each tracks its own status/result so partial failure
 * is allowed (Threads succeeds while Bluesky fails, etc.). Long FKs (post-flow convention).
 */
@Getter
@Entity
@Table(name = "post_targets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTarget extends BaseTimeEntity {

    static final int MAX_RETRIES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "social_account_id", nullable = false)
    private Long socialAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostTargetStatus status = PostTargetStatus.PENDING;

    /** Platform post id/uri (Threads media id, Bluesky at:// uri). */
    @Column(name = "platform_post_id")
    private String platformPostId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static PostTarget create(Long postId, Long socialAccountId) {
        PostTarget t = new PostTarget();
        t.postId = postId;
        t.socialAccountId = socialAccountId;
        t.status = PostTargetStatus.PENDING;
        return t;
    }

    /** Backfill/legacy: create a target already in a terminal/known state. */
    public static PostTarget of(Long postId, Long socialAccountId, PostTargetStatus status,
                                String platformPostId, Instant publishedAt) {
        PostTarget t = create(postId, socialAccountId);
        t.status = status;
        t.platformPostId = platformPostId;
        t.publishedAt = publishedAt;
        return t;
    }

    public void startPublishing() {
        this.status = PostTargetStatus.PUBLISHING;
    }

    public void markPublished(String platformPostId) {
        this.status = PostTargetStatus.PUBLISHED;
        this.platformPostId = platformPostId;
        this.publishedAt = Instant.now();
        this.errorMessage = null;
    }

    /** Transient failure — go back to PENDING for the next tick (bounded by MAX_RETRIES). */
    public void markRetry(String error) {
        this.status = PostTargetStatus.PENDING;
        this.errorMessage = truncate(error);
        this.retryCount++;
    }

    public void markFailed(String error) {
        this.status = PostTargetStatus.FAILED;
        this.errorMessage = truncate(error);
        this.retryCount++;
    }

    public void markReconnectRequired() {
        this.status = PostTargetStatus.RECONNECT_REQUIRED;
    }

    /** 발행됐던 게시물이 플랫폼에서 삭제됨을 감지(404). 답글 달 곳이 없어 자동화 대상에서 빠진다. */
    public void markDeletedOnPlatform() {
        this.status = PostTargetStatus.DELETED_ON_PLATFORM;
    }

    public boolean retriesExhausted() {
        return retryCount + 1 >= MAX_RETRIES;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
