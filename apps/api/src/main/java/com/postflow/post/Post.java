package com.postflow.post;

import com.postflow.common.entity.BaseTimeEntity;
import com.postflow.common.jpa.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "text")
    private List<String> hashtags = new ArrayList<>();

    @Column(length = 500)
    private String cta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostStatus status = PostStatus.DRAFT;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "threads_media_id")
    private String threadsMediaId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    public static Post create(Long userId, String content, List<String> hashtags, String cta) {
        Post p = new Post();
        p.userId = userId;
        p.content = content;
        p.hashtags = hashtags != null ? hashtags : new ArrayList<>();
        p.cta = cta;
        p.status = PostStatus.DRAFT;
        return p;
    }

    public void updateContent(String content) {
        if (content != null && !content.isBlank()) {
            this.content = content;
        }
    }

    public void updateMeta(List<String> hashtags, String cta) {
        if (hashtags != null) {
            this.hashtags = hashtags;
        }
        if (cta != null) {
            this.cta = cta;
        }
    }

    public void schedule(Instant at) {
        this.scheduledAt = at;
        this.status = PostStatus.SCHEDULED;
    }

    public void unschedule() {
        this.scheduledAt = null;
        this.status = PostStatus.DRAFT;
    }

    public void startPublishing() {
        this.status = PostStatus.PUBLISHING;
    }

    public void markPublished(String threadsMediaId) {
        this.status = PostStatus.PUBLISHED;
        this.threadsMediaId = threadsMediaId;
        this.publishedAt = java.time.Instant.now();
        this.errorMessage = null;
    }

    /** Transient failure — stay SCHEDULED for the next cron tick, bump retry count. */
    public void markRetry(String error) {
        this.status = PostStatus.SCHEDULED;
        this.errorMessage = truncate(error);
        this.retryCount++;
    }

    /** Permanent failure after exhausting retries. */
    public void markFailed(String error) {
        this.status = PostStatus.FAILED;
        this.errorMessage = truncate(error);
        this.retryCount++;
    }

    public void markReconnectRequired() {
        this.status = PostStatus.RECONNECT_REQUIRED;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
