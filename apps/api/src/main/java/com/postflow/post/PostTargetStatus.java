package com.postflow.post;

/** Per-channel publish status for a {@link PostTarget}. */
public enum PostTargetStatus {
    PENDING,             // 아직 발행 전(초안/예약)
    PUBLISHING,          // 발행 진행 중
    PUBLISHED,           // 발행 완료
    FAILED,              // 재시도 소진 후 실패
    RECONNECT_REQUIRED   // 채널 토큰 만료/재연결 필요
}
