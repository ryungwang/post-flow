package com.postflow.threads.dto;

import com.postflow.threads.api.ThreadsReply;

import java.util.List;

/**
 * 댓글 조회 결과. {@code available=false}면 Threads 앱이 아직 {@code threads_manage_replies}
 * 검수 승인을 못 받아 조회가 불가한 상태(코드10 권한 오류) — 프론트가 "검수 후 이용 가능" 안내.
 */
public record RepliesResult(boolean available, List<ThreadsReply> replies) {
    public static RepliesResult ok(List<ThreadsReply> replies) {
        return new RepliesResult(true, replies);
    }

    public static RepliesResult unavailable() {
        return new RepliesResult(false, List.of());
    }
}
