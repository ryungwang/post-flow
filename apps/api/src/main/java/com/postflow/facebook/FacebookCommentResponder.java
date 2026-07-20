package com.postflow.facebook;

import com.postflow.facebook.FacebookApiClient.FbComment;
import com.postflow.social.CommentResponder;
import com.postflow.social.InboundComment;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 페이스북 페이지 댓글 자동응답. 계정의 {@code accessToken}은 페이지 토큰이라 그대로 쓴다.
 *
 * <p>검수 권한 두 개가 필요하다 — 댓글 읽기는 {@code pages_read_user_content},
 * 답글 작성은 {@code pages_manage_engagement}. 미승인 상태에서는 Graph가 403을 주고,
 * 자동화 잡이 그 채널만 건너뛴다(다른 채널은 계속 처리된다).
 */
@Component
public class FacebookCommentResponder implements CommentResponder {

    private final FacebookApiClient client;

    public FacebookCommentResponder(FacebookApiClient client) {
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.FACEBOOK;
    }

    @Override
    public List<InboundComment> fetchComments(SocialAccount account, String platformPostId) {
        List<FbComment> comments = client.getComments(platformPostId, account.getAccessToken());
        return comments.stream()
                .map(c -> new InboundComment(c.id(), c.message(), c.authorName()))
                .toList();
    }

    @Override
    public void reply(SocialAccount account, String platformPostId, String commentId, String text) {
        // 게시물이 아니라 '댓글'에 달아야 답글로 중첩되고 작성자에게 알림이 간다.
        client.replyToComment(commentId, account.getAccessToken(), text);
    }
}
