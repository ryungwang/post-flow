package com.postflow.bluesky;

import com.postflow.bluesky.BlueskyApiClient.PostThreadView;
import com.postflow.social.CommentResponder;
import com.postflow.social.InboundComment;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 블루스카이(AT Protocol) 댓글 자동응답 + 첫 댓글. 답글은 createRecord(app.bsky.feed.post)에 reply
 * 강한참조(root/parent uri+cid)를 넣어 만든다. 댓글 조회는 공개 AppView라 인증이 필요 없고, 답글 생성만
 * 세션 accessJwt를 쓰므로 만료 시 refreshJwt로 갱신·재시도한다(퍼블리셔와 동일 패턴).
 */
@Component
public class BlueskyCommentResponder implements CommentResponder {

    private final SocialAccountRepository repository;
    private final BlueskyApiClient client;

    public BlueskyCommentResponder(SocialAccountRepository repository, BlueskyApiClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.BLUESKY;
    }

    @Override
    public List<InboundComment> fetchComments(SocialAccount account, String platformPostId) {
        List<PostThreadView> replies = client.getReplies(platformPostId);
        return replies.stream()
                .filter(r -> r.post() != null && r.post().uri() != null)
                .map(r -> new InboundComment(
                        r.post().uri(),
                        r.post().record() != null ? r.post().record().text() : "",
                        r.post().author() != null ? r.post().author().handle() : null))
                .toList();
    }

    @Override
    public void reply(SocialAccount account, String platformPostId, String commentId, String text) {
        // root=원 게시물, parent=댓글 → 알림이 그 사람에게 가고 스레드가 올바르게 붙는다.
        withFreshSession(account, jwt -> client.createReply(
                account.getExternalId(), jwt, text, platformPostId, commentId));
    }

    @Override
    public void commentOnPost(SocialAccount account, String platformPostId, String text) {
        // 첫 댓글 = 내 게시물 자체에 대한 최상위 답글. root=parent=원 게시물.
        withFreshSession(account, jwt -> client.createReply(
                account.getExternalId(), jwt, text, platformPostId, platformPostId));
    }

    /** accessJwt로 시도하고, 만료면 refreshJwt로 갱신·저장 후 1회 재시도. 갱신 실패면 재연결 필요 표시. */
    private void withFreshSession(SocialAccount account, Function<String, String> call) {
        try {
            call.apply(account.getAccessToken());
        } catch (BlueskyAuthException expired) {
            try {
                BlueskySession refreshed = client.refreshSession(account.getRefreshToken());
                account.applyBlueskySession(refreshed.accessJwt(), refreshed.refreshJwt());
                repository.save(account);
                call.apply(refreshed.accessJwt());
            } catch (BlueskyAuthException refreshFailed) {
                account.markReconnectRequired();
                repository.save(account);
                throw refreshFailed;
            }
        }
    }
}
