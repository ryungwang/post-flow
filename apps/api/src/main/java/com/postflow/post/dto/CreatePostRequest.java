package com.postflow.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * @param content     post body (≤1000 chars = DB 컬럼 상한). 플랫폼별 실제 한도(Threads 500 등)는 발행 시 검증.
 * @param hashtags    optional hashtags (no '#')
 * @param cta         optional call-to-action line
 * @param scheduledAt optional — if present the post is created as SCHEDULED, else DRAFT
 */
public record CreatePostRequest(
        @NotBlank @Size(max = 1000) String content,
        List<String> hashtags,
        String cta,
        String mediaUrl,
        Instant scheduledAt,
        List<Long> channelIds,  // 발행 대상 채널(SocialAccount id) 다중선택. 비면 기본 채널.
        @Size(max = 1000) String firstComment  // 발행 후 자동으로 다는 첫 댓글(제휴 대가성 고지문 등). null이면 없음.
) {
}
