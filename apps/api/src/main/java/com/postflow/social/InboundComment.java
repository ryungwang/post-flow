package com.postflow.social;

/** 플랫폼에서 읽어온 댓글 한 건(자동응답 매칭 대상). */
public record InboundComment(
        String id,      // 플랫폼의 댓글 id — 답글을 달 대상이자 중복 방지 키
        String text,
        String author   // 표시용 핸들(없으면 null)
) {
}
