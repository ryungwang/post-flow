package com.postflow.threads;

import com.postflow.social.ConnectionStatus;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import com.postflow.threads.api.ThreadsTokenResponse;
import com.postflow.threads.dto.ThreadsStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Owns Threads connection lifecycle: code→token exchange, in-place token refresh,
 * and connection status. Tokens are long-lived (60-day) and have no refresh_token —
 * see PRD → Threads Integration.
 */
@Service
public class SocialAccountService {

    private final SocialAccountRepository repository;
    private final ThreadsApiClient apiClient;

    public SocialAccountService(SocialAccountRepository repository, ThreadsApiClient apiClient) {
        this.repository = repository;
        this.apiClient = apiClient;
    }

    /** Exchange an authorization code for a long-lived token and store the connection. */
    @Transactional
    public void connectFromCode(Long userId, String code) {
        ThreadsTokenResponse shortLived = apiClient.exchangeCodeForShortLivedToken(code);
        ThreadsTokenResponse longLived = apiClient.exchangeForLongLivedToken(shortLived.accessToken());

        Instant expiresAt = expiryFrom(longLived.expiresIn());
        String threadsUserId = shortLived.userId();
        String token = longLived.accessToken();

        repository.findByUserIdAndProvider(userId, SocialProvider.THREADS)
                .ifPresentOrElse(
                        existing -> existing.reconnect(threadsUserId, token, expiresAt),
                        () -> repository.save(SocialAccount.connect(userId, threadsUserId, token, expiresAt)));
    }

    @Transactional
    public void refresh(SocialAccount account) {
        try {
            ThreadsTokenResponse refreshed = apiClient.refreshLongLivedToken(account.getAccessToken());
            account.applyRefresh(refreshed.accessToken(), expiryFrom(refreshed.expiresIn()));
        } catch (ThreadsApiException e) {
            account.markReconnectRequired();
        }
    }

    @Transactional(readOnly = true)
    public ThreadsStatusResponse status(Long userId) {
        return find(userId)
                .map(a -> new ThreadsStatusResponse(
                        a.getStatus() == ConnectionStatus.CONNECTED, a.getStatus().name(), a.getExpiresAt()))
                .orElseGet(ThreadsStatusResponse::notConnected);
    }

    @Transactional(readOnly = true)
    public Optional<SocialAccount> find(Long userId) {
        return repository.findByUserIdAndProvider(userId, SocialProvider.THREADS);
    }

    private Instant expiryFrom(Long expiresInSeconds) {
        long seconds = expiresInSeconds != null ? expiresInSeconds : 0L;
        return Instant.now().plusSeconds(seconds);
    }
}
