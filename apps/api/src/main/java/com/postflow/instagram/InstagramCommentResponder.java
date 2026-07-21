package com.postflow.instagram;

import com.postflow.instagram.InstagramApiClient.IgComment;
import com.postflow.social.CommentResponder;
import com.postflow.social.InboundComment;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 인스타그램 댓글 자동응답. IG 계정의 {@code accessToken}은 연결된 페이지 토큰이라 그대로 쓴다
 * ({@code externalId} = IG 비즈니스 계정 id). 검수 권한 {@code instagram_manage_comments} 필요 —
 * 미승인 시 Graph가 권한 오류를 주고 자동화 잡이 그 채널만 건너뛴다.
 *
 * <p>IG 댓글은 게시물이 삭제되면 404/코드100을 주며, 그때 {@code PostDeletedException}이
 * 던져져 자동화가 타겟을 정리한다(다른 플랫폼과 동일 self-heal).
 */
@Component
public class InstagramCommentResponder implements CommentResponder {

    private final InstagramApiClient client;

    public InstagramCommentResponder(InstagramApiClient client) {
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.INSTAGRAM;
    }

    @Override
    public List<InboundComment> fetchComments(SocialAccount account, String platformPostId) {
        List<IgComment> comments = client.getComments(platformPostId, account.getAccessToken());
        return comments.stream()
                .map(c -> new InboundComment(c.id(), c.text(), c.username()))
                .toList();
    }

    @Override
    public void reply(SocialAccount account, String platformPostId, String commentId, String text) {
        // 게시물이 아니라 '댓글'에 답글 → 답글로 중첩되고 작성자에게 알림이 간다.
        client.replyToComment(commentId, account.getAccessToken(), text);
    }
}
