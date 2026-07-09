package com.postflow.post;

public enum PostStatus {
    DRAFT,
    SCHEDULED,
    PUBLISHING,
    PUBLISHED,
    PARTIAL,            // 멀티채널 발행에서 일부만 성공(나머지 실패) — 부분 실패 허용
    FAILED,
    RECONNECT_REQUIRED,
    DELETED
}
