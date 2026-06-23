package com.postflow.threads.dto;

import java.time.Instant;

public record ThreadsAccountDto(
        Long id,
        String username,
        String name,
        String profilePictureUrl,
        String biography,
        Long followersCount,
        Long views,
        Long likes,
        Long replies,
        Long reposts,
        Long quotes,
        String status,
        boolean isDefault,
        Instant expiresAt
) {
}
