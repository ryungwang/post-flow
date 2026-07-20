package com.postflow.threads;

import com.postflow.social.CommentResponder;
import com.postflow.social.InboundComment;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialProvider;
import com.postflow.threads.api.ThreadsReply;
import org.springframework.stereotype.Component;

import java.util.List;

/** Threads 댓글 자동응답 — 기존 CommentAutomationJob의 Threads 전용 로직을 이관. */
@Component
public class ThreadsCommentResponder implements CommentResponder {

    private final ThreadsApiClient apiClient;
    private final ThreadsPublishService publishService;

    public ThreadsCommentResponder(ThreadsApiClient apiClient, ThreadsPublishService publishService) {
        this.apiClient = apiClient;
        this.publishService = publishService;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.THREADS;
    }

    @Override
    public List<InboundComment> fetchComments(SocialAccount account, String platformPostId) {
        List<ThreadsReply> replies = apiClient.getReplies(platformPostId, account.getAccessToken());
        return replies.stream()
                .map(r -> new InboundComment(r.id(), r.text(), r.username()))
                .toList();
    }

    @Override
    public void reply(SocialAccount account, String platformPostId, String commentId, String text) {
        // Threads는 답글 대상 id만 있으면 되고, 원 게시물 id는 쓰지 않는다.
        publishService.publishReply(account.getThreadsUserId(), account.getAccessToken(), text, commentId);
    }
}
