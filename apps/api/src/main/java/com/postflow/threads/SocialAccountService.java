package com.postflow.threads;

import com.postflow.social.ConnectionStatus;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.threads.api.ThreadsTokenResponse;
import com.postflow.threads.dto.ThreadsAccountDto;
import com.postflow.threads.dto.ThreadsAccountPostDto;
import com.postflow.threads.dto.ThreadsStatusResponse;
import com.postflow.user.PlanLimitException;
import com.postflow.user.PlanPolicy;
import com.postflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PostRepository postRepository;

    public SocialAccountService(SocialAccountRepository repository, ThreadsApiClient apiClient,
                                UserService userService, PostRepository postRepository) {
        this.repository = repository;
        this.apiClient = apiClient;
        this.userService = userService;
        this.postRepository = postRepository;
    }

    /**
     * 연결된 Threads 계정의 실제 게시물 목록(최신순) + 각 게시물이 PostFlow 발행인지 표시.
     * accountId=null이면 기본/첫 계정. 미연결이면 빈 목록.
     */
    @Transactional(readOnly = true)
    public List<ThreadsAccountPostDto> accountPosts(Long userId, Long accountId, int limit) {
        SocialAccount account = accountId != null
                ? repository.findById(accountId).filter(a -> a.getUserId().equals(userId)).orElse(null)
                : find(userId).orElse(null);
        if (account == null || account.getThreadsUserId() == null) {
            return List.of();
        }
        // PostFlow에서 발행된 게시물의 Threads media id 집합(대조용).
        Set<String> mine = postRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(Post::getThreadsMediaId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        String token = account.getAccessToken();
        return apiClient.fetchUserPosts(account.getThreadsUserId(), token, limit).stream()
                // 게시물별 참여 지표(좋아요·댓글·리포스트·공유). 실패/미제공은 null.
                .map(p -> ThreadsAccountPostDto.of(p, mine.contains(p.id()), apiClient.fetchMediaInsights(p.id(), token)))
                .toList();
    }

    /** 계정 인사이트: 팔로워 수 + 팔로워 인구통계(연령/성별/국가/도시). 미연결이면 null. */
    @Transactional(readOnly = true)
    public com.postflow.threads.dto.ThreadsInsightsDto accountInsights(Long userId, Long accountId) {
        SocialAccount account = accountId != null
                ? repository.findById(accountId).filter(a -> a.getUserId().equals(userId)).orElse(null)
                : find(userId).orElse(null);
        if (account == null || account.getThreadsUserId() == null) {
            return null;
        }
        String uid = account.getThreadsUserId();
        String token = account.getAccessToken();
        Long followers = apiClient.fetchFollowers(uid, token);
        var demo = new com.postflow.threads.dto.ThreadsInsightsDto.Demographics(
                apiClient.fetchFollowerDemographics(uid, token, "age"),
                apiClient.fetchFollowerDemographics(uid, token, "gender"),
                apiClient.fetchFollowerDemographics(uid, token, "country"),
                apiClient.fetchFollowerDemographics(uid, token, "city"));
        return new com.postflow.threads.dto.ThreadsInsightsDto(followers, demo);
    }

    /** 특정 게시물의 댓글(답글) 목록. 미연결/실패 시 빈 목록. */
    @Transactional(readOnly = true)
    public List<com.postflow.threads.api.ThreadsReply> repliesFor(Long userId, String mediaId) {
        SocialAccount account = find(userId).orElse(null);
        if (account == null || account.getAccessToken() == null) {
            return List.of();
        }
        try {
            return apiClient.getReplies(mediaId, account.getAccessToken());
        } catch (RuntimeException e) {
            return List.of();
        }
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
        long until = Instant.now().getEpochSecond();
        long since = until - 30L * 24 * 3600; // last 30 days for engagement
        for (SocialAccount a : repository.findByUserIdAndProviderOrderByIdAsc(userId, THREADS)) {
            String biography = null;
            Long followers = null;
            com.postflow.threads.api.ThreadsInsights eng = null;
            if (a.getStatus() == ConnectionStatus.CONNECTED) {
                com.postflow.threads.api.ThreadsUsername p = apiClient.fetchProfile(a.getAccessToken());
                if (p != null) {
                    a.updateProfile(p.username(), p.name(), p.profilePictureUrl()); // cache handle/name/picture
                    biography = p.biography();
                }
                followers = apiClient.fetchFollowers(a.getThreadsUserId(), a.getAccessToken());
                eng = apiClient.fetchEngagement(a.getThreadsUserId(), a.getAccessToken(), since, until);
            }
            out.add(new ThreadsAccountDto(
                    a.getId(),
                    a.getUsername() != null ? a.getUsername() : a.getThreadsUserId(),
                    a.getName(),
                    a.getProfilePictureUrl(),
                    biography,
                    followers,
                    eng != null ? eng.value("views") : null,
                    eng != null ? eng.value("likes") : null,
                    eng != null ? eng.value("replies") : null,
                    eng != null ? eng.value("reposts") : null,
                    eng != null ? eng.value("quotes") : null,
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

    /**
     * Meta deauthorize/data-deletion 콜백 처리 — 해당 Threads 계정(앱스코프 user id)으로 연결된
     * 모든 SocialAccount를 삭제(토큰 폐기). 삭제 건수 반환. 매칭 없으면 0(멱등).
     */
    @Transactional
    public int disconnectByThreadsUserId(String threadsUserId) {
        List<SocialAccount> accounts = repository.findByProviderAndThreadsUserId(THREADS, threadsUserId);
        if (accounts.isEmpty()) {
            return 0;
        }
        repository.deleteAll(accounts);
        repository.flush();
        // 각 사용자별로 기본 계정이 사라졌으면 남은 계정 중 하나를 기본으로 승격.
        accounts.stream().map(SocialAccount::getUserId).distinct().forEach(uid ->
                repository.findByUserIdAndProviderOrderByIdAsc(uid, THREADS).stream()
                        .findFirst().ifPresent(a -> a.setDefault(true)));
        return accounts.size();
    }

    /** Connecting an additional account requires the Pro plan (multi-channel). */
    @Transactional(readOnly = true)
    public void assertCanAddAccount(Long userId) {
        boolean multi = PlanPolicy.canMultiAccount(userService.getById(userId).getPlan());
        long count = repository.countByUserIdAndProvider(userId, THREADS);
        if (!multi && count >= 1) {
            throw new PlanLimitException("다중 채널 연결은 Pro 플랜부터 가능해요. (현재 플랜은 1채널)");
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
