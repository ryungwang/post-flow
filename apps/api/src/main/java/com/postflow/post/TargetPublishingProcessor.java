package com.postflow.post;

import com.postflow.social.ConnectionStatus;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Per-target publish state transitions (fan-out). Mirrors PublishingProcessor but at the
 * channel level so external HTTP runs outside a transaction:
 * {@link #claim} (txn) → caller publishes (no txn) → {@link #complete}/{@link #fail} (txn).
 * Each transition recomputes the parent post's aggregate status.
 */
@Service
public class TargetPublishingProcessor {

    private final PostRepository postRepository;
    private final PostTargetRepository targetRepository;
    private final SocialAccountRepository socialAccountRepository;

    public TargetPublishingProcessor(PostRepository postRepository,
                                     PostTargetRepository targetRepository,
                                     SocialAccountRepository socialAccountRepository) {
        this.postRepository = postRepository;
        this.targetRepository = targetRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    /** Claim a PENDING target: verify its channel is connected, mark PUBLISHING. */
    @Transactional
    public Optional<TargetTask> claim(Long targetId) {
        PostTarget target = targetRepository.findById(targetId).orElse(null);
        if (target == null || target.getStatus() != PostTargetStatus.PENDING) {
            return Optional.empty();
        }
        Post post = postRepository.findById(target.getPostId()).orElse(null);
        if (post == null) {
            return Optional.empty();
        }
        SocialAccount account = socialAccountRepository.findById(target.getSocialAccountId()).orElse(null);
        if (account == null || account.getStatus() != ConnectionStatus.CONNECTED || isExpired(account)) {
            target.markReconnectRequired();
            recompute(post);
            return Optional.empty();
        }
        target.startPublishing();
        recompute(post);
        return Optional.of(new TargetTask(
                targetId, account.getId(), account.getProvider(),
                post.toPublishText(), post.getMediaUrl()));
    }

    @Transactional
    public void complete(Long targetId, String platformPostId) {
        targetRepository.findById(targetId).ifPresent(t -> {
            t.markPublished(platformPostId);
            postRepository.findById(t.getPostId()).ifPresent(this::recompute);
        });
    }

    @Transactional
    public void fail(Long targetId, String error) {
        targetRepository.findById(targetId).ifPresent(t -> {
            if (t.retriesExhausted()) {
                t.markFailed(error);
            } else {
                t.markRetry(error);
            }
            postRepository.findById(t.getPostId()).ifPresent(this::recompute);
        });
    }

    private void recompute(Post post) {
        post.applyTargetAggregate(targetRepository.findByPostIdOrderByIdAsc(post.getId()));
    }

    private boolean isExpired(SocialAccount account) {
        return account.getExpiresAt() != null && account.getExpiresAt().isBefore(Instant.now());
    }

    public record TargetTask(Long targetId, Long accountId, SocialProvider provider,
                             String content, String mediaUrl) {
    }

    /** Immediate publish helper: the PENDING targets of a post that should be attempted now. */
    @Transactional(readOnly = true)
    public List<Long> pendingTargetIds(Long postId) {
        return targetRepository.findByPostIdOrderByIdAsc(postId).stream()
                .filter(t -> t.getStatus() == PostTargetStatus.PENDING)
                .map(PostTarget::getId)
                .toList();
    }
}
