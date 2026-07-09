package com.postflow.post.dto;

/** Per-channel publish target of a post (for the UI's multi-channel selection + status). */
public record PostTargetDto(
        Long socialAccountId,
        String provider,      // THREADS / BLUESKY
        String channel,       // @username / handle (표시용)
        String status,        // PENDING / PUBLISHING / PUBLISHED / FAILED / RECONNECT_REQUIRED
        String platformPostId,
        String error
) {
}
