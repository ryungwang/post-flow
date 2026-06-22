package com.postflow.threads.dto;

import java.time.Instant;

public record ThreadsAccountDto(
        Long id,
        String username,
        String name,
        String profilePictureUrl,
        Long followersCount,
        String status,
        boolean isDefault,
        Instant expiresAt
) {
}
