package com.postflow.post.dto;

import com.postflow.post.Post;

import java.time.Instant;

public record PostDto(
        Long id,
        String content,
        String status,
        Instant scheduledAt,
        Instant publishedAt,
        String threadsMediaId,
        Instant createdAt
) {
    public static PostDto from(Post post) {
        return new PostDto(
                post.getId(),
                post.getContent(),
                post.getStatus().name(),
                post.getScheduledAt(),
                post.getPublishedAt(),
                post.getThreadsMediaId(),
                post.getCreatedAt());
    }
}
