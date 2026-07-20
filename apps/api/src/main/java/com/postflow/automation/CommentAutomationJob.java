package com.postflow.automation;

import com.postflow.post.Post;
import com.postflow.post.PostRepository;
import com.postflow.post.PostStatus;
import com.postflow.post.PostTarget;
import com.postflow.post.PostTargetRepository;
import com.postflow.post.PostTargetStatus;
import com.postflow.social.CommentResponder;
import com.postflow.social.CommentResponderRegistry;
import com.postflow.social.ConnectionStatus;
import com.postflow.social.InboundComment;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.PublishException;
import com.postflow.user.PlanPolicy;
import com.postflow.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 발행된 게시물의 댓글을 주기적으로 훑어 키워드가 맞으면 한 번씩 자동 답글을 단다.
 *
 * <p>플랫폼별 호출은 {@link CommentResponder}가 담당한다 — 이 잡은 어떤 SNS인지 모른다.
 * 대상은 {@link PostTarget}(발행된 채널별 결과)이라, 한 글을 여러 채널에 팬아웃했으면
 * 각 채널의 댓글이 각각 처리된다. 지원 구현이 없는 플랫폼은 조용히 건너뛴다.
 */
@Component
public class CommentAutomationJob {

    private static final Logger log = LoggerFactory.getLogger(CommentAutomationJob.class);

    private final CommentRuleRepository ruleRepository;
    private final CommentReplyRepository replyRepository;
    private final CommentRuleService ruleService;
    private final PostRepository postRepository;
    private final PostTargetRepository postTargetRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final CommentResponderRegistry responders;
    private final UserService userService;

    public CommentAutomationJob(CommentRuleRepository ruleRepository,
                                CommentReplyRepository replyRepository,
                                CommentRuleService ruleService,
                                PostRepository postRepository,
                                PostTargetRepository postTargetRepository,
                                SocialAccountRepository socialAccountRepository,
                                CommentResponderRegistry responders,
                                UserService userService) {
        this.ruleRepository = ruleRepository;
        this.replyRepository = replyRepository;
        this.ruleService = ruleService;
        this.postRepository = postRepository;
        this.postTargetRepository = postTargetRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.responders = responders;
        this.userService = userService;
    }

    @Scheduled(fixedDelayString = "${automation.comment-poll-ms:120000}", initialDelay = 30_000)
    public void poll() {
        List<CommentRule> rules = ruleRepository.findByActiveTrue();
        for (CommentRule rule : rules) {
            try {
                // Pro 강등 유저의 규칙은 실행하지 않음(자동화 = Pro 전용). 결제 상태 변화 반영.
                if (!PlanPolicy.canAutomation(userService.getById(rule.getUserId()).getPlan())) {
                    continue;
                }
                processRule(rule);
            } catch (Exception e) {
                log.warn("Comment rule {} failed: {}", rule.getId(), e.getMessage());
            }
        }
    }

    // No @Transactional: invoked via self-call (proxy can't apply it) and contains external HTTP;
    // each repository save is its own transaction, and the dedup check makes it idempotent.
    private void processRule(CommentRule rule) {
        String replyText = ruleService.resolveReply(rule);
        for (Post post : targetPosts(rule)) {
            for (PostTarget target : publishedTargets(post)) {
                try {
                    processTarget(rule, post, target, replyText);
                } catch (Exception e) {
                    // 한 채널이 실패해도 나머지 채널은 계속 처리한다.
                    log.warn("Comment automation failed for post {} target {}: {}",
                            post.getId(), target.getId(), e.getMessage());
                }
            }
        }
    }

    private void processTarget(CommentRule rule, Post post, PostTarget target, String replyText) {
        SocialAccount account = usableAccount(target, post.getUserId());
        if (account == null) {
            return; // 미연결·만료·타인 계정 → 건너뜀
        }
        CommentResponder responder = responders.find(account.getProvider()).orElse(null);
        if (responder == null) {
            return; // 이 플랫폼은 댓글 자동응답 미지원(정상 상황)
        }

        for (InboundComment comment : responder.fetchComments(account, target.getPlatformPostId())) {
            if (!rule.matches(comment.text())) {
                continue;
            }
            if (replyRepository.existsByRuleIdAndProviderAndCommentId(
                    rule.getId(), account.getProvider(), comment.id())) {
                continue;
            }
            try {
                responder.reply(account, target.getPlatformPostId(), comment.id(), replyText);
                replyRepository.save(CommentReply.of(
                        rule.getId(), post.getId(), account.getProvider(), comment.id()));
                log.info("Auto-replied to {} comment {} on post {} (rule {})",
                        account.getProvider(), comment.id(), post.getId(), rule.getId());
            } catch (PublishException e) {
                log.warn("Auto-reply failed for comment {}: {}", comment.id(), e.getMessage());
            }
        }
    }

    /** 이 채널로 실제 발행된 결과만 — 발행 id가 있어야 댓글을 조회할 수 있다. */
    private List<PostTarget> publishedTargets(Post post) {
        return postTargetRepository.findByPostIdOrderByIdAsc(post.getId()).stream()
                .filter(t -> t.getStatus() == PostTargetStatus.PUBLISHED)
                .filter(t -> t.getPlatformPostId() != null && !t.getPlatformPostId().isBlank())
                .toList();
    }

    /** 발행에 쓰인 그 계정이 여전히 이 유저 소유이고 사용 가능할 때만 반환. */
    private SocialAccount usableAccount(PostTarget target, Long userId) {
        if (target.getSocialAccountId() == null) {
            return null;
        }
        return socialAccountRepository.findById(target.getSocialAccountId())
                .filter(a -> a.getUserId().equals(userId))
                .filter(a -> a.getStatus() == ConnectionStatus.CONNECTED)
                .filter(a -> !isExpired(a))
                .orElse(null);
    }

    private List<Post> targetPosts(CommentRule rule) {
        List<Post> candidates = rule.getPostId() != null
                ? postRepository.findById(rule.getPostId()).map(List::of).orElse(List.of())
                : postRepository.findByUserIdOrderByCreatedAtDesc(rule.getUserId());
        return candidates.stream()
                .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
                .toList();
    }

    private boolean isExpired(SocialAccount account) {
        return account.getExpiresAt() != null && account.getExpiresAt().isBefore(Instant.now());
    }
}
