package com.postflow.post.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Partial update. {@code content} updates the body when present; {@code scheduledAt}
 * (re)schedules when present.
 */
public record UpdatePostRequest(
        @Size(max = 500) String content,
        Instant scheduledAt
) {
}
