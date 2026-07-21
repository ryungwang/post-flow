package com.postflow.social;

/**
 * 발행됐던 게시물이 플랫폼에서 삭제됐음을 나타낸다(플랫폼 조회 시 404).
 * {@link CommentResponder} 구현이 던지면, 자동화 잡이 해당 타겟을 삭제됨으로 표시하고
 * 이후 대상에서 제외한다 — 답글 달 곳이 사라졌기 때문.
 */
public class PostDeletedException extends PublishException {
    public PostDeletedException(String message) {
        super(message);
    }
}
