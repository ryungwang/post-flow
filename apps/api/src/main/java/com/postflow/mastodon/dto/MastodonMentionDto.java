package com.postflow.mastodon.dto;

/** 나를 멘션한 게시물 한 건. */
public record MastodonMentionDto(
        String id,          // 알림 id
        String statusId,    // 답글을 달 대상 게시물 id
        String authorHandle,
        String authorName,
        String authorAvatar,
        String text,
        String createdAt,
        String permalink
) {
}
