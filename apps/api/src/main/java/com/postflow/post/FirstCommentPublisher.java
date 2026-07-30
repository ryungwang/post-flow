package com.postflow.post;

import com.postflow.social.CommentResponderRegistry;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 발행된 게시물에 첫 댓글(제휴 대가성 고지문 등)을 <b>best-effort</b>로 단다. 발행 팬아웃에서 각 타겟이
 * platformPostId를 받은 직후 호출된다. 미지원 플랫폼(댓글 responder 없음)이나 실패는 조용히 스킵 —
 * 첫 댓글 실패가 발행 자체를 되돌리지 않는다. HTTP 호출이라 발행 트랜잭션 밖에서 실행한다.
 */
@Component
public class FirstCommentPublisher {

    private static final Logger log = LoggerFactory.getLogger(FirstCommentPublisher.class);

    private final CommentResponderRegistry registry;
    private final SocialAccountRepository accounts;

    public FirstCommentPublisher(CommentResponderRegistry registry, SocialAccountRepository accounts) {
        this.registry = registry;
        this.accounts = accounts;
    }

    public void post(SocialProvider provider, Long accountId, String platformPostId, String text) {
        if (text == null || text.isBlank() || platformPostId == null || platformPostId.isBlank()) {
            return;
        }
        registry.find(provider).ifPresent(responder ->
                accounts.findById(accountId).ifPresent(account -> {
                    try {
                        responder.commentOnPost(account, platformPostId, text);
                    } catch (RuntimeException e) {
                        log.warn("첫 댓글 게시 실패 (provider {} post {}): {}", provider, platformPostId, e.getMessage());
                    }
                }));
    }
}
