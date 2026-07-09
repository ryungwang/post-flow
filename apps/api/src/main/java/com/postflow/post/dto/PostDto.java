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
        List<PostTargetDto> targets
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
                ContentScorer.score(post.getContent(), post.getHashtags(), post.getCta()),
                post.getStatus().name(),
                post.getScheduledAt(),
                post.getPublishedAt(),
                post.getThreadsMediaId(),
                post.getCreatedAt(),
                targets != null ? targets : List.of());
    }

    /** Without targets (fallback for callers that don't enrich). */
    public static PostDto from(Post post) {
        return from(post, List.of());
    }
}
