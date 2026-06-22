package com.postflow.threads.dto;

import java.time.Instant;

public record ThreadsStatusResponse(
        boolean connected,
        String status,
        Instant expiresAt
) {
    public static ThreadsStatusResponse notConnected() {
        return new ThreadsStatusResponse(false, "NOT_CONNECTED", null);
    }
}
