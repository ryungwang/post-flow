package com.postflow.mastodon.dto;

/** 내 마스토돈 게시물 한 건. */
public record MastodonPostDto(
        String id,
        String text,       // content(HTML)에서 태그를 벗긴 본문
        String createdAt,
        long favourites,
        long reblogs,
        long replies,
        String imageUrl,   // 첫 이미지 미리보기(있으면)
        String permalink   // 인스턴스의 게시물 링크
) {
}
