package com.postflow.threads.dto;

import com.postflow.threads.api.ThreadsUserPost;

/**
 * 연결된 Threads 계정의 실제 게시물 + PostFlow 발행 여부.
 * {@code fromPostflow}=이 게시물이 PostFlow에서 발행됐는지(로컬 threadsMediaId 대조).
 */
public record ThreadsAccountPostDto(
        String id,
        String text,
        String timestamp,
        String permalink,
        String mediaType,
        String mediaUrl,
        boolean fromPostflow
) {
    public static ThreadsAccountPostDto of(ThreadsUserPost p, boolean fromPostflow) {
        return new ThreadsAccountPostDto(p.id(), p.text(), p.timestamp(), p.permalink(),
                p.mediaType(), p.mediaUrl(), fromPostflow);
    }
}
