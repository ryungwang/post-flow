package com.postflow.auth.dto;

import com.postflow.user.User;

public record UserDto(
        Long id,
        String email,
        String name,
        String profileImage,
        String plan
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProfileImage(),
                user.getPlan().name());
    }
}
