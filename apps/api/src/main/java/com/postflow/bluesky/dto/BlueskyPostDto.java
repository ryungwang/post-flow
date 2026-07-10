package com.postflow.bluesky.dto;

/** 내 Bluesky 게시물 한 건(공개 피드 기반). */
public record BlueskyPostDto(
        String id,        // rkey
        String text,
        String createdAt,
        long likes,
        long reposts,
        long replies,
        String imageUrl,  // 첫 이미지 썸네일(있으면)
        String permalink  // bsky.app 링크
) {
}
