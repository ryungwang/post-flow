package com.postflow.threads.dto;

import com.postflow.threads.api.ThreadsInsights;
import com.postflow.threads.api.ThreadsUserPost;

/**
 * 연결된 Threads 계정의 실제 게시물 + PostFlow 발행 여부 + 참여 지표(insights).
 * {@code fromPostflow}=이 게시물이 PostFlow에서 발행됐는지(로컬 threadsMediaId 대조).
 * 지표는 null 가능(insights 미제공/실패).
 */
public record ThreadsAccountPostDto(
        String id,
        String text,
        String timestamp,
        String permalink,
        String mediaType,
        String mediaUrl,
        boolean fromPostflow,
        Long views,
        Long likes,
        Long replies,
        Long reposts,
        Long quotes,
        Long shares
) {
    public static ThreadsAccountPostDto of(ThreadsUserPost p, boolean fromPostflow, ThreadsInsights ins) {
        return new ThreadsAccountPostDto(
                p.id(), p.text(), p.timestamp(), p.permalink(), p.mediaType(), p.mediaUrl(), fromPostflow,
                ins == null ? null : ins.value("views"),
                ins == null ? null : ins.value("likes"),
                ins == null ? null : ins.value("replies"),
                ins == null ? null : ins.value("reposts"),
                ins == null ? null : ins.value("quotes"),
                ins == null ? null : ins.value("shares"));
    }
}
