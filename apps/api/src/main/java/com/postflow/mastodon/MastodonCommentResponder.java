package com.postflow.mastodon;

import com.postflow.mastodon.MastodonApiClient.MastodonStatusItem;
import com.postflow.social.CommentResponder;
import com.postflow.social.InboundComment;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 마스토돈 댓글 자동응답. 검수·유료 티어가 없어 토큰 범위(read:statuses + write:statuses)만
 * 맞으면 바로 동작한다 — Threads가 앱 검수를 기다리는 동안 같은 기능을 실제로 검증할 수 있다.
 */
@Component
public class MastodonCommentResponder implements CommentResponder {

    private final MastodonApiClient client;

    public MastodonCommentResponder(MastodonApiClient client) {
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.MASTODON;
    }

    @Override
    public List<InboundComment> fetchComments(SocialAccount account, String platformPostId) {
        List<MastodonStatusItem> replies = client.getStatusReplies(
                account.getInstanceUrl(), account.getAccessToken(), platformPostId);
        return replies.stream()
                .map(s -> new InboundComment(
                        s.id(),
                        // 본문이 HTML이라 키워드 매칭 전에 평문으로 바꾼다(태그가 매칭을 방해함).
                        MastodonReadService.stripHtml(s.content()),
                        s.account() != null ? s.account().acct() : null))
                .toList();
    }

    @Override
    public void reply(SocialAccount account, String platformPostId, String commentId, String text) {
        // 원 게시물이 아니라 '댓글'에 답글을 달아야 알림이 그 사람에게 간다.
        client.createReply(account.getInstanceUrl(), account.getAccessToken(), text, commentId);
    }
}
