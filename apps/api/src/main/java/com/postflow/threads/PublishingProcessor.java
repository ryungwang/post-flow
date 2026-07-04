package com.postflow.threads;

import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.post.PostStatus;
import com.postflow.social.ConnectionStatus;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Transactional state transitions for publishing, split so external HTTP (which can poll
 * for seconds) runs OUTSIDE any DB transaction:
 * {@link #claim} (txn) → caller publishes (no txn) → {@link #complete}/{@link #fail} (txn).
 */
@Service
public class PublishingProcessor {

    static final int MAX_RETRIES = 3;

    private final PostRepository postRepository;
    private final SocialAccountRepository socialAccountRepository;

    public PublishingProcessor(PostRepository postRepository,
                               SocialAccountRepository socialAccountRepository) {
        this.postRepository = postRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    private static final Set<PostStatus> DUE = EnumSet.of(PostStatus.SCHEDULED);
    private static final Set<PostStatus> IMMEDIATE = EnumSet.of(
            PostStatus.DRAFT, PostStatus.SCHEDULED, PostStatus.FAILED, PostStatus.RECONNECT_REQUIRED);

    /** Claim a due (SCHEDULED) post for the cron publisher. */
    @Transactional
    public Optional<PublishTask> claim(Long postId) {
        return claim(postId, DUE);
    }

    /** Claim a post for immediate publish (publish-now): allows DRAFT/SCHEDULED/retry states. */
    @Transactional
    public Optional<PublishTask> claimImmediate(Long postId) {
        return claim(postId, IMMEDIATE);
    }

    private Optional<PublishTask> claim(Long postId, Set<PostStatus> allowed) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || !allowed.contains(post.getStatus())) {
            return Optional.empty();
        }
        SocialAccount account = resolveAccount(post);
        if (account == null || account.getStatus() != ConnectionStatus.CONNECTED || isExpired(account)) {
            post.markReconnectRequired();
            return Optional.empty();
        }
        post.startPublishing();
        return Optional.of(new PublishTask(
                postId, account.getThreadsUserId(), account.getAccessToken(),
                post.toPublishText(), post.getMediaUrl())); // 본문+해시태그+CTA 전체
    }

    @Transactional
    public void complete(Long postId, String mediaId) {
        postRepository.findById(postId).ifPresent(p -> p.markPublished(mediaId));
    }

    @Transactional
    public void fail(Long postId, String error) {
        postRepository.findById(postId).ifPresent(p -> {
            if (p.getRetryCount() + 1 >= MAX_RETRIES) {
                p.markFailed(error);
            } else {
                p.markRetry(error);
            }
        });
    }

    /** The post's chosen account (if owned), else the user's default/first account. */
    private SocialAccount resolveAccount(Post post) {
        if (post.getSocialAccountId() != null) {
            SocialAccount chosen = socialAccountRepository.findById(post.getSocialAccountId()).orElse(null);
            if (chosen != null && chosen.getUserId().equals(post.getUserId())) {
                return chosen;
            }
        }
        return socialAccountRepository
                .findFirstByUserIdAndProviderAndIsDefaultTrue(post.getUserId(), SocialProvider.THREADS)
                .or(() -> socialAccountRepository
                        .findByUserIdAndProviderOrderByIdAsc(post.getUserId(), SocialProvider.THREADS)
                        .stream().findFirst())
                .orElse(null);
    }

    private boolean isExpired(SocialAccount account) {
        return account.getExpiresAt() != null && account.getExpiresAt().isBefore(Instant.now());
    }

    public record PublishTask(Long postId, String threadsUserId, String accessToken,
                              String content, String mediaUrl) {
    }
}
