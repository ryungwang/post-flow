package com.postflow.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * @param content     post body (≤500 chars, Threads limit)
 * @param scheduledAt optional — if present the post is created as SCHEDULED, else DRAFT
 */
public record CreatePostRequest(
        @NotBlank @Size(max = 500) String content,
        Instant scheduledAt
) {
}
