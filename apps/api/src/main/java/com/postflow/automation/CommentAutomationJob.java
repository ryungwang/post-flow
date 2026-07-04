package com.postflow.automation;

import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.post.PostStatus;
import com.postflow.social.ConnectionStatus;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import com.postflow.threads.ThreadsApiException;
import com.postflow.threads.ThreadsApiClient;
import com.postflow.threads.ThreadsPublishService;
import com.postflow.threads.api.ThreadsReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls published posts for keyword comments and auto-replies once per comment.
 * No-ops when the user has no connected Threads account (e.g. keys not yet configured).
 */
@Component
public class CommentAutomationJob {

    private static final Logger log = LoggerFactory.getLogger(CommentAutomationJob.class);

    private final CommentRuleRepository ruleRepository;
    private final CommentReplyRepository replyRepository;
    private final CommentRuleService ruleService;
    private final PostRepository postRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final ThreadsApiClient apiClient;
    private final ThreadsPublishService publishService;
    private final com.postflow.user.UserService userService;

    public CommentAutomationJob(CommentRuleRepository ruleRepository,
                                CommentReplyRepository replyRepository,
                                CommentRuleService ruleService,
                                PostRepository postRepository,
                                SocialAccountRepository socialAccountRepository,
                                ThreadsApiClient apiClient,
                                ThreadsPublishService publishService,
                                com.postflow.user.UserService userService) {
        this.ruleRepository = ruleRepository;
        this.replyRepository = replyRepository;
        this.ruleService = ruleService;
        this.postRepository = postRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.apiClient = apiClient;
        this.publishService = publishService;
        this.userService = userService;
    }

    @Scheduled(fixedDelayString = "${automation.comment-poll-ms:120000}", initialDelay = 30_000)
    public void poll() {
        List<CommentRule> rules = ruleRepository.findByActiveTrue();
        for (CommentRule rule : rules) {
            try {
                // Pro 강등 유저의 규칙은 실행하지 않음(자동화 = Pro 전용). 결제 상태 변화 반영.
                if (!com.postflow.user.PlanPolicy.canAutomation(userService.getById(rule.getUserId()).getPlan())) {
                    continue;
                }
                processRule(rule);
            } catch (Exception e) {
                log.warn("Comment rule {} failed: {}", rule.getId(), e.getMessage());
            }
        }
    }

    // No @Transactional: invoked via self-call (proxy can't apply it) and contains external HTTP;
    // each repository save is its own transaction, and dedup (existsByRuleIdAndThreadsReplyId) makes it idempotent.
    private void processRule(CommentRule rule) {
        String replyText = ruleService.resolveReply(rule);
        for (Post post : targetPosts(rule)) {
            // each post may have been published from a different account → use that account's token
            SocialAccount account = resolveAccount(post);
            if (account == null) {
                continue; // post's account not connected (or keys absent) → skip
            }
            List<ThreadsReply> replies = apiClient.getReplies(post.getThreadsMediaId(), account.getAccessToken());
            for (ThreadsReply reply : replies) {
                if (!rule.matches(reply.text())) {
                    continue;
                }
                if (replyRepository.existsByRuleIdAndThreadsReplyId(rule.getId(), reply.id())) {
                    continue;
                }
                try {
                    publishService.publishReply(account.getThreadsUserId(), account.getAccessToken(), replyText, reply.id());
                    replyRepository.save(CommentReply.of(rule.getId(), post.getId(), reply.id()));
                    log.info("Auto-replied to comment {} on post {} (rule {})", reply.id(), post.getId(), rule.getId());
                } catch (ThreadsApiException e) {
                    log.warn("Auto-reply failed for comment {}: {}", reply.id(), e.getMessage());
                }
            }
        }
    }

    /** The post's chosen account (if owned + usable), else the user's default/first usable account. */
    private SocialAccount resolveAccount(Post post) {
        if (post.getSocialAccountId() != null) {
            SocialAccount chosen = socialAccountRepository.findById(post.getSocialAccountId())
                    .filter(a -> a.getUserId().equals(post.getUserId()))
                    .orElse(null);
            if (chosen != null && chosen.getStatus() == ConnectionStatus.CONNECTED && !isExpired(chosen)) {
                return chosen;
            }
        }
        return socialAccountRepository
                .findFirstByUserIdAndProviderAndIsDefaultTrue(post.getUserId(), SocialProvider.THREADS)
                .or(() -> socialAccountRepository
                        .findByUserIdAndProviderOrderByIdAsc(post.getUserId(), SocialProvider.THREADS)
                        .stream().findFirst())
                .filter(a -> a.getStatus() == ConnectionStatus.CONNECTED && !isExpired(a))
                .orElse(null);
    }

    private List<Post> targetPosts(CommentRule rule) {
        List<Post> candidates = rule.getPostId() != null
                ? postRepository.findById(rule.getPostId()).map(List::of).orElse(List.of())
                : postRepository.findByUserIdOrderByCreatedAtDesc(rule.getUserId());
        return candidates.stream()
                .filter(p -> p.getStatus() == PostStatus.PUBLISHED && p.getThreadsMediaId() != null)
                .toList();
    }

    private boolean isExpired(SocialAccount account) {
        return account.getExpiresAt() != null && account.getExpiresAt().isBefore(Instant.now());
    }
}
