package com.postflow.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * @param content     post body (≤500 chars, Threads limit)
 * @param hashtags    optional hashtags (no '#')
 * @param cta         optional call-to-action line
 * @param scheduledAt optional — if present the post is created as SCHEDULED, else DRAFT
 */
public record CreatePostRequest(
        @NotBlank @Size(max = 500) String content,
        List<String> hashtags,
        String cta,
        String mediaUrl,
        Instant scheduledAt,
        List<Long> channelIds  // 발행 대상 채널(SocialAccount id) 다중선택. 비면 기본 채널.
) {
}
