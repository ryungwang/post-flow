package com.postflow.auth;

public record GoogleUserInfo(
        String email,
        String name,
        String pictureUrl
) {
}
