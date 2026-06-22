package com.postflow.social;

import com.postflow.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Connected social account. Threads has no refresh_token — the long-lived access token
 * (60-day) is renewed in place, so we track {@code expiresAt} / {@code lastRefreshedAt}
 * instead of a refresh token. See PRD → Threads Integration.
 */
@Getter
@Entity
@Table(name = "social_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider = SocialProvider.THREADS;

    @Column(name = "access_token", nullable = false, length = 1024)
    private String accessToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConnectionStatus status = ConnectionStatus.CONNECTED;
}
