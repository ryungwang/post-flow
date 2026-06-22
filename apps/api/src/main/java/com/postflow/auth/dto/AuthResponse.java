package com.postflow.auth.dto;

public record AuthResponse(
        String token,
        UserDto user
) {
}
