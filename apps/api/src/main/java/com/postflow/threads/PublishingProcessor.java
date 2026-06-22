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
import java.util.Optional;

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

    /** Validate + mark PUBLISHING. Returns a publish task, or empty if not publishable. */
    @Transactional
    public Optional<PublishTask> claim(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || post.getStatus() != PostStatus.SCHEDULED) {
            return Optional.empty();
        }
        SocialAccount account = socialAccountRepository
                .findByUserIdAndProvider(post.getUserId(), SocialProvider.THREADS)
                .orElse(null);
        if (account == null || account.getStatus() != ConnectionStatus.CONNECTED || isExpired(account)) {
            post.markReconnectRequired();
            return Optional.empty();
        }
        post.startPublishing();
        return Optional.of(new PublishTask(
                postId, account.getThreadsUserId(), account.getAccessToken(), post.getContent()));
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

    private boolean isExpired(SocialAccount account) {
        return account.getExpiresAt() != null && account.getExpiresAt().isBefore(Instant.now());
    }

    public record PublishTask(Long postId, String threadsUserId, String accessToken, String content) {
    }
}
