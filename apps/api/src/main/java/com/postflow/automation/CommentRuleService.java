package com.postflow.automation;

import com.postflow.automation.dto.CommentRuleDto;
import com.postflow.automation.dto.CommentRuleRequest;
import com.postflow.post.PostRepository;
import com.postflow.roi.CtaLink;
import com.postflow.roi.CtaLinkRepository;
import com.postflow.roi.CtaLinkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentRuleService {

    private final CommentRuleRepository ruleRepository;
    private final CtaLinkRepository ctaLinkRepository;
    private final CtaLinkService ctaLinkService;
    private final PostRepository postRepository;

    public CommentRuleService(CommentRuleRepository ruleRepository,
                              CtaLinkRepository ctaLinkRepository,
                              CtaLinkService ctaLinkService,
                              PostRepository postRepository) {
        this.ruleRepository = ruleRepository;
        this.ctaLinkRepository = ctaLinkRepository;
        this.ctaLinkService = ctaLinkService;
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public List<CommentRuleDto> list(Long userId) {
        return ruleRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(CommentRuleDto::from).toList();
    }

    @Transactional
    public CommentRuleDto create(Long userId, CommentRuleRequest req) {
        assertRefsOwned(userId, req.postId(), req.ctaLinkId());
        CommentRule rule = CommentRule.create(userId, req.postId(), req.keyword(), req.replyTemplate(), req.ctaLinkId());
        return CommentRuleDto.from(ruleRepository.save(rule));
    }

    @Transactional
    public CommentRuleDto update(Long userId, Long id, CommentRuleRequest req) {
        CommentRule rule = loadOwned(userId, id);
        assertRefsOwned(userId, req.postId(), req.ctaLinkId());
        rule.update(req.keyword(), req.replyTemplate(), req.ctaLinkId(), req.active());
        return CommentRuleDto.from(rule);
    }

    /** Client-supplied post / CTA-link references must belong to the user. */
    private void assertRefsOwned(Long userId, Long postId, Long ctaLinkId) {
        if (postId != null) {
            postRepository.findById(postId)
                    .filter(p -> p.getUserId().equals(userId))
                    .orElseThrow(() -> new IllegalArgumentException("post not found"));
        }
        if (ctaLinkId != null) {
            ctaLinkRepository.findById(ctaLinkId)
                    .filter(l -> l.getUserId().equals(userId))
                    .orElseThrow(() -> new IllegalArgumentException("link not found"));
        }
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ruleRepository.delete(loadOwned(userId, id));
    }

    /** Preview: does {@code sampleComment} trigger the rule, and what would the reply be? */
    @Transactional(readOnly = true)
    public TestResult test(Long userId, Long id, String sampleComment) {
        CommentRule rule = loadOwned(userId, id);
        boolean matched = rule.matches(sampleComment);
        return new TestResult(matched, matched ? resolveReply(rule) : null);
    }

    /** Resolve the reply text, substituting {link} with the tracking short URL. */
    public String resolveReply(CommentRule rule) {
        String link = "";
        if (rule.getCtaLinkId() != null) {
            link = ctaLinkRepository.findById(rule.getCtaLinkId())
                    .map(this::shortUrl).orElse("");
        }
        return rule.getReplyTemplate().replace("{link}", link).trim();
    }

    private String shortUrl(CtaLink link) {
        return ctaLinkService.baseUrl() + "/r/" + link.getSlug();
    }

    private CommentRule loadOwned(Long userId, Long id) {
        CommentRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("rule not found"));
        if (!rule.getUserId().equals(userId)) {
            throw new IllegalArgumentException("rule not found");
        }
        return rule;
    }

    public record TestResult(boolean matched, String replyText) {
    }
}
