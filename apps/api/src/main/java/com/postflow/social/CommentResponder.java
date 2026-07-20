package com.postflow.social;

import java.util.List;

/**
 * 댓글 자동응답의 플랫폼별 구현. {@link Publisher}와 같은 모양으로, 구현체를 등록하면
 * {@link CommentResponderRegistry}가 자동으로 주워 담는다.
 *
 * <p>모든 플랫폼이 댓글 조회/답글을 지원하지는 않는다(LinkedIn 개인 프로필은 파트너 승인 필요,
 * Instagram은 별도 권한). 구현체가 없는 플랫폼은 레지스트리가 비어 있는 값을 돌려주고,
 * 자동화 잡은 그 채널을 조용히 건너뛴다.
 */
public interface CommentResponder {

    SocialProvider provider();

    /** 발행된 게시물에 달린 댓글 목록. */
    List<InboundComment> fetchComments(SocialAccount account, String platformPostId);

    /** 특정 댓글에 답글을 단다. */
    void reply(SocialAccount account, String platformPostId, String commentId, String text);
}
