package com.postflow.social.dto;

import com.postflow.social.SocialAccount;

/**
 * Lightweight cross-provider channel view (DB fields only — no live platform API call).
 * Used for the channel-connect list and the publish channel picker. Rich Threads insights
 * stay on the Threads-specific endpoints.
 */
public record ChannelDto(
        Long id,
        String provider,
        String username,
        String name,
        String profilePictureUrl,
        String status,
        boolean isDefault
) {
    public static ChannelDto from(SocialAccount a) {
        String display = a.getUsername() != null ? a.getUsername() : a.getExternalId();
        return new ChannelDto(
                a.getId(),
                a.getProvider().name(),
                display,
                a.getName(),
                a.getProfilePictureUrl(),
                a.getStatus().name(),
                a.isDefault());
    }
}
