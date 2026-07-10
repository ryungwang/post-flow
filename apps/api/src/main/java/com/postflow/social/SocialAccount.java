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

    /** Threads-side user id (returned at token exchange); used in Threads publish endpoints. */
    @Column(name = "threads_user_id")
    private String threadsUserId;

    /** Generic platform account id (Threads=threadsUserId, Bluesky=DID). Cross-provider key. */
    @Column(name = "external_id")
    private String externalId;

    /** Bluesky handle (e.g. name.bsky.social). Null for Threads. */
    @Column(name = "handle")
    private String handle;

    /** Mastodon instance base URL (e.g. https://mastodon.social) — federated hosts differ per account. Null otherwise. */
    @Column(name = "instance_url")
    private String instanceUrl;

    @Column(name = "access_token", nullable = false, length = 1024)
    private String accessToken;

    /** Refresh token (Bluesky refreshJwt — rotates on refresh). Threads renews in place → null. */
    @Column(name = "refresh_token", length = 1024)
    private String refreshToken;

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
        a.externalId = threadsUserId;
        a.accessToken = accessToken;
        a.expiresAt = expiresAt;
        a.lastRefreshedAt = Instant.now();
        a.status = ConnectionStatus.CONNECTED;
        return a;
    }

    /**
     * Connect a Bluesky account. Auth = app-password → session (we store the session
     * JWTs, never the app password). {@code did} = AT Protocol account id; {@code handle}
     * = user handle. accessJwt is short-lived → refreshed via refreshJwt on publish.
     */
    public static SocialAccount connectBluesky(Long userId, String did, String handle,
                                               String accessJwt, String refreshJwt) {
        SocialAccount a = new SocialAccount();
        a.userId = userId;
        a.provider = SocialProvider.BLUESKY;
        a.externalId = did;
        a.handle = handle;
        a.username = handle;
        a.name = handle;
        a.accessToken = accessJwt;
        a.refreshToken = refreshJwt;
        a.expiresAt = null; // accessJwt exp is handled by refresh-on-401, not this field
        a.lastRefreshedAt = Instant.now();
        a.status = ConnectionStatus.CONNECTED;
        return a;
    }

    /** Re-link a Bluesky account on reconnect (fresh session from a new app password). */
    public void reconnectBluesky(String did, String handle, String accessJwt, String refreshJwt) {
        this.externalId = did;
        this.handle = handle;
        this.username = handle;
        this.accessToken = accessJwt;
        this.refreshToken = refreshJwt;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
    }

    /** Persist a refreshed Bluesky session (rotated access + refresh JWTs). */
    public void applyBlueskySession(String accessJwt, String refreshJwt) {
        this.accessToken = accessJwt;
        this.refreshToken = refreshJwt;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
    }

    /**
     * Connect a LinkedIn account (OAuth2). {@code memberId} = OpenID {@code sub} (person id,
     * stored bare — the publisher builds {@code urn:li:person:{id}}). {@code refreshToken} is
     * only issued to approved apps; null otherwise (then reconnect is required on expiry).
     */
    public static SocialAccount connectLinkedin(Long userId, String memberId, String name,
                                                String profilePictureUrl, String accessToken,
                                                String refreshToken, Instant expiresAt) {
        SocialAccount a = new SocialAccount();
        a.userId = userId;
        a.provider = SocialProvider.LINKEDIN;
        a.externalId = memberId;
        a.username = name;
        a.name = name;
        a.profilePictureUrl = profilePictureUrl;
        a.accessToken = accessToken;
        a.refreshToken = refreshToken;
        a.expiresAt = expiresAt;
        a.lastRefreshedAt = Instant.now();
        a.status = ConnectionStatus.CONNECTED;
        return a;
    }

    /** Re-link a LinkedIn account on reconnect (fresh OAuth token). */
    public void reconnectLinkedin(String memberId, String name, String profilePictureUrl,
                                  String accessToken, String refreshToken, Instant expiresAt) {
        this.externalId = memberId;
        this.username = name;
        this.name = name;
        this.profilePictureUrl = profilePictureUrl;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
    }

    /** Persist a refreshed LinkedIn token (refresh-token grant); refresh token may rotate. */
    public void applyLinkedinToken(String accessToken, String refreshToken, Instant expiresAt) {
        this.accessToken = accessToken;
        if (refreshToken != null) {
            this.refreshToken = refreshToken;
        }
        this.expiresAt = expiresAt;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
    }

    /**
     * Connect a Mastodon account. Auth = a personal access token the user creates in their
     * instance (Settings → Development). {@code instanceUrl} = the instance API host;
     * {@code externalId} = the account id on that instance; {@code acct} = the local handle.
     * Tokens are long-lived (no expiry) → no refresh token.
     */
    public static SocialAccount connectMastodon(Long userId, String instanceUrl, String accountId,
                                                String acct, String displayName, String avatar,
                                                String accessToken) {
        SocialAccount a = new SocialAccount();
        a.userId = userId;
        a.provider = SocialProvider.MASTODON;
        a.instanceUrl = instanceUrl;
        a.externalId = accountId;
        a.handle = acct;
        a.username = acct;
        a.name = displayName;
        a.profilePictureUrl = avatar;
        a.accessToken = accessToken;
        a.expiresAt = null;
        a.lastRefreshedAt = Instant.now();
        a.status = ConnectionStatus.CONNECTED;
        return a;
    }

    /** Re-link a Mastodon account on reconnect (fresh token, possibly new instance). */
    public void reconnectMastodon(String instanceUrl, String accountId, String acct,
                                  String displayName, String avatar, String accessToken) {
        this.instanceUrl = instanceUrl;
        this.externalId = accountId;
        this.handle = acct;
        this.username = acct;
        this.name = displayName;
        this.profilePictureUrl = avatar;
        this.accessToken = accessToken;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
    }

    /**
     * Connect a Facebook Page. Auth = the Page access token (obtained via the user's OAuth then
     * {@code /me/accounts}). {@code pageId} = the Page id; {@code pageName} = its display name.
     * Page tokens from a long-lived user token are effectively non-expiring → no refresh token.
     */
    public static SocialAccount connectFacebookPage(Long userId, String pageId, String pageName,
                                                    String pictureUrl, String pageAccessToken) {
        SocialAccount a = new SocialAccount();
        a.userId = userId;
        a.provider = SocialProvider.FACEBOOK;
        a.externalId = pageId;
        a.username = pageName;
        a.name = pageName;
        a.profilePictureUrl = pictureUrl;
        a.accessToken = pageAccessToken;
        a.expiresAt = null;
        a.lastRefreshedAt = Instant.now();
        a.status = ConnectionStatus.CONNECTED;
        return a;
    }

    /** Re-link a Facebook Page on reconnect (fresh page token / name). */
    public void reconnectFacebookPage(String pageName, String pictureUrl, String pageAccessToken) {
        this.username = pageName;
        this.name = pageName;
        this.profilePictureUrl = pictureUrl;
        this.accessToken = pageAccessToken;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
    }

    /**
     * Connect an Instagram Business account. Discovered from a linked Facebook Page, so auth =
     * the Page access token. {@code igUserId} = the IG Business account id (used in the content
     * publishing endpoints). Instagram requires an image on every feed post.
     */
    public static SocialAccount connectInstagram(Long userId, String igUserId, String username,
                                                 String pictureUrl, String pageAccessToken) {
        SocialAccount a = new SocialAccount();
        a.userId = userId;
        a.provider = SocialProvider.INSTAGRAM;
        a.externalId = igUserId;
        a.username = username;
        a.name = username;
        a.profilePictureUrl = pictureUrl;
        a.accessToken = pageAccessToken;
        a.expiresAt = null;
        a.lastRefreshedAt = Instant.now();
        a.status = ConnectionStatus.CONNECTED;
        return a;
    }

    /** Re-link an Instagram account on reconnect (fresh page token / profile). */
    public void reconnectInstagram(String username, String pictureUrl, String pageAccessToken) {
        this.username = username;
        this.name = username;
        this.profilePictureUrl = pictureUrl;
        this.accessToken = pageAccessToken;
        this.lastRefreshedAt = Instant.now();
        this.status = ConnectionStatus.CONNECTED;
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
        this.externalId = threadsUserId;
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
