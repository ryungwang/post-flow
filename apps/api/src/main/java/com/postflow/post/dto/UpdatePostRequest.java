package com.postflow.post.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Partial update. Non-null fields are applied.
 */
public record UpdatePostRequest(
        @Size(max = 500) String content,
        List<String> hashtags,
        String cta,
        Instant scheduledAt
) {
}
