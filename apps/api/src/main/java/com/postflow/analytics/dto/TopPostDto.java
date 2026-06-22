package com.postflow.analytics.dto;

public record TopPostDto(
        Long postId,
        String content,
        long views,
        long likes,
        long replies
) {
}
