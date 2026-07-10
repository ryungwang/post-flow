package com.postflow.bluesky.dto;

/** Bluesky 계정 인사이트(프로필 + 최근 게시물 참여 집계). */
public record BlueskyInsightsDto(
        String handle,
        String displayName,
        String avatar,
        long followers,
        long posts,
        long totalLikes,
        long totalReposts,
        long totalReplies,
        int sampledPosts   // 집계에 쓴 최근 게시물 수
) {
}
