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

    /** Threads-side user id (returned at token exchange); used in publish endpoints. */
    @Column(name = "threads_user_id")
    private String threadsUserId;

    @Column(name = "access_token", nullable = false, length = 1024)
    private String accessToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Column(name = "username")
    private String username;

    @Column(name = "name")
    private String name;

    @Column(name = "profile_picture_url", length = 2048)
    private String profilePictureUrl;

    /** Whether this is the account used for publishing by default. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConnectionStatus status = ConnectionStatus.CONNECTED;

    public static SocialAccount connect(Long userId, String threadsUserId,
                                        String accessToken, Instant expiresAt) {
        SocialAccount a = new SocialAccount();
        a.userId = userId;
        a.provider = SocialProvider.THREADS;
        a.threadsUserId = threadsUserId;
        a.accessToken = accessToken;
        a.expiresAt = expiresAt;
        a.lastRefreshedAt = Instant.now();
        a.status = ConnectionStatus.CONNECTED;
        return a;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /** Update cached profile (username/name/picture) from the Threads API. */
    public void updateProfile(String username, String name, String profilePictureUrl) {
        if (username != null) {
            this.username = username;
        }
        this.name = name;
        this.profilePictureUrl = profilePictureUrl;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /** Re-link on reconnect (new token + threads user id). */
    public void reconnect(String threadsUserId, String accessToken, Instant expiresAt) {
        this.threadsUserId = threadsUserId;
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
    }

    /** In-place long-lived token renewal (Threads has no refresh_token). */
    public void applyRefresh(String accessToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
    }

    public void markReconnectRequired() {
        this.status = ConnectionStatus.RECONNECT_REQUIRED;
    }
}
