package com.postflow.mastodon.dto;

/** 마스토돈 계정 인사이트(프로필 + 최근 게시물 참여 집계). */
public record MastodonInsightsDto(
        String handle,
        String displayName,
        String avatar,
        String instance,
        long followers,
        long following,
        long posts,
        long totalFavourites,
        long totalReblogs,
        long totalReplies,
        int sampledPosts   // 집계에 쓴 최근 게시물 수
) {
}
