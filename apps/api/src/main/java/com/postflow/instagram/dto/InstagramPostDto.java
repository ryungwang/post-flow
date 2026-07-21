package com.postflow.instagram.dto;

/** 내 인스타그램 게시물 한 건. */
public record InstagramPostDto(
        String id,
        String caption,
        String createdAt,
        long likes,
        long comments,
        String imageUrl,
        String permalink
) {
}
