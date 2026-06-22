package com.postflow.threads.dto;

import java.time.Instant;

public record ThreadsAccountDto(
        Long id,
        String username,
        String status,
        boolean isDefault,
        Instant expiresAt
) {
}
