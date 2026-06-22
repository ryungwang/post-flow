package com.postflow.threads;

import com.postflow.social.ConnectionStatus;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import com.postflow.threads.api.ThreadsTokenResponse;
import com.postflow.threads.dto.ThreadsAccountDto;
import com.postflow.threads.dto.ThreadsStatusResponse;
import com.postflow.user.PlanLimitException;
import com.postflow.user.PlanPolicy;
import com.postflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Threads connection lifecycle with multi-account support: code→token exchange, in-place
 * token refresh, default-account selection, listing/disconnect. Tokens are long-lived
 * (60-day, no refresh_token) — see PRD → Threads Integration.
 */
@Service
public class SocialAccountService {

    private static final SocialProvider THREADS = SocialProvider.THREADS;

    private final SocialAccountRepository repository;
    private final ThreadsApiClient apiClient;
    private final UserService userService;

    public SocialAccountService(SocialAccountRepository repository, ThreadsApiClient apiClient, UserService userService) {
        this.repository = repository;
        this.apiClient = apiClient;
        this.userService = userService;
    }

    /** Exchange an authorization code for a long-lived token and store/refresh the connection. */
    @Transactional
    public void connectFromCode(Long userId, String code) {
        ThreadsTokenResponse shortLived = apiClient.exchangeCodeForShortLivedToken(code);
        ThreadsTokenResponse longLived = apiClient.exchangeForLongLivedToken(shortLived.accessToken());

        Instant expiresAt = expiryFrom(longLived.expiresIn());
        String threadsUserId = shortLived.userId();
        String token = longLived.accessToken();
        com.postflow.threads.api.ThreadsUsername profile = apiClient.fetchProfile(token);
        boolean multi = PlanPolicy.canMultiAccount(userService.getById(userId).getPlan());

        SocialAccount target = repository
                .findByUserIdAndProviderAndThreadsUserId(userId, THREADS, threadsUserId)
                .map(existing -> { existing.reconnect(threadsUserId, token, expiresAt); return existing; })
                .orElseGet(() -> {
                    List<SocialAccount> mine = repository.findByUserIdAndProviderOrderByIdAsc(userId, THREADS);
                    if (!multi && !mine.isEmpty()) {
                        // single-account plans: replace the existing connection
                        SocialAccount only = mine.get(0);
                        only.reconnect(threadsUserId, token, expiresAt);
                        return only;
                    }
                    return repository.save(SocialAccount.connect(userId, threadsUserId, token, expiresAt));
                });
        if (profile != null) {
            target.updateProfile(profile.username(), profile.name(), profile.profilePictureUrl());
        }
        makeDefault(userId, target); // newly connected becomes the active account
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

    @Transactional
    public List<ThreadsAccountDto> list(Long userId) {
        List<ThreadsAccountDto> out = new java.util.ArrayList<>();
        for (SocialAccount a : repository.findByUserIdAndProviderOrderByIdAsc(userId, THREADS)) {
            // one-time enrichment: cache real @handle/name/picture if missing or still the numeric id
            if (a.getStatus() == ConnectionStatus.CONNECTED
                    && (a.getName() == null || a.getUsername() == null || a.getUsername().matches("\\d+"))) {
                com.postflow.threads.api.ThreadsUsername p = apiClient.fetchProfile(a.getAccessToken());
                if (p != null) {
                    a.updateProfile(p.username(), p.name(), p.profilePictureUrl());
                }
            }
            Long followers = a.getStatus() == ConnectionStatus.CONNECTED
                    ? apiClient.fetchFollowers(a.getThreadsUserId(), a.getAccessToken()) : null;
            out.add(new ThreadsAccountDto(
                    a.getId(),
                    a.getUsername() != null ? a.getUsername() : a.getThreadsUserId(),
                    a.getName(),
                    a.getProfilePictureUrl(),
                    followers,
                    a.getStatus().name(),
                    a.isDefault(),
                    a.getExpiresAt()));
        }
        return out;
    }

    @Transactional
    public void setDefaultAccount(Long userId, Long accountId) {
        SocialAccount account = repository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("account not found"));
        makeDefault(userId, account);
    }

    @Transactional
    public void disconnect(Long userId, Long accountId) {
        SocialAccount account = repository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("account not found"));
        boolean wasDefault = account.isDefault();
        repository.delete(account);
        repository.flush(); // ensure the deleted row isn't returned when re-selecting a default
        if (wasDefault) {
            repository.findByUserIdAndProviderOrderByIdAsc(userId, THREADS).stream()
                    .findFirst().ifPresent(a -> a.setDefault(true));
        }
    }

    /** Connecting an additional account requires the Business plan. */
    @Transactional(readOnly = true)
    public void assertCanAddAccount(Long userId) {
        boolean multi = PlanPolicy.canMultiAccount(userService.getById(userId).getPlan());
        long count = repository.countByUserIdAndProvider(userId, THREADS);
        if (!multi && count >= 1) {
            throw new PlanLimitException("다중 계정 연결은 Business 플랜부터 가능해요. (현재 플랜은 1계정)");
        }
    }

    /** The default account used for publishing. */
    @Transactional(readOnly = true)
    public Optional<SocialAccount> find(Long userId) {
        Optional<SocialAccount> def = repository.findFirstByUserIdAndProviderAndIsDefaultTrue(userId, THREADS);
        if (def.isPresent()) {
            return def;
        }
        return repository.findByUserIdAndProviderOrderByIdAsc(userId, THREADS).stream().findFirst();
    }

    private void makeDefault(Long userId, SocialAccount target) {
        for (SocialAccount a : repository.findByUserIdAndProviderOrderByIdAsc(userId, THREADS)) {
            a.setDefault(a.getId() != null && a.getId().equals(target.getId()));
        }
        target.setDefault(true);
    }

    private Instant expiryFrom(Long expiresInSeconds) {
        long seconds = expiresInSeconds != null ? expiresInSeconds : 0L;
        return Instant.now().plusSeconds(seconds);
    }
}
