package com.postflow.instagram.dto;

/** 인스타그램 계정 인사이트(프로필 + 최근 게시물 참여 집계). */
public record InstagramInsightsDto(
        String username,
        String avatar,
        long followers,
        long following,
        long posts,
        long totalLikes,
        long totalComments,
        int sampledPosts   // 집계에 쓴 최근 게시물 수
) {
}
