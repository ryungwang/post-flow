package com.postflow.post.dto;

import com.postflow.ai.content.ContentScorer;
import com.postflow.post.Post;

import java.time.Instant;
import java.util.List;

public record PostDto(
        Long id,
        String content,
        List<String> hashtags,
        String cta,
        String mediaUrl,
        Long socialAccountId,
        int score,
        String status,
        Instant scheduledAt,
        Instant publishedAt,
        String threadsMediaId,
        Instant createdAt,
        List<PostTargetDto> targets,
        String firstComment
) {
    /** Enriched with per-channel targets (PostService supplies them — needs SocialAccount join). */
    public static PostDto from(Post post, List<PostTargetDto> targets) {
        return new PostDto(
                post.getId(),
                post.getContent(),
                post.getHashtags(),
                post.getCta(),
                post.getMediaUrl(),
                post.getSocialAccountId(),
                // 제휴 글(첫 댓글=고지문 있음)은 제휴 전용 기준으로 채점 — CTA·해시태그·길이 감점 안 함.
                post.getFirstComment() != null
                        ? ContentScorer.scoreAffiliate(post.getContent(), post.getHashtags())
                        : ContentScorer.score(post.getContent(), post.getHashtags(), post.getCta()),
                post.getStatus().name(),
                post.getScheduledAt(),
                post.getPublishedAt(),
                post.getThreadsMediaId(),
                post.getCreatedAt(),
                targets != null ? targets : List.of(),
                post.getFirstComment());
    }

    /** Without targets (fallback for callers that don't enrich). */
    public static PostDto from(Post post) {
        return from(post, List.of());
    }
}
