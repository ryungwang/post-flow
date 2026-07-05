package com.postflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReplyRepository extends JpaRepository<CommentReply, Long> {
    boolean existsByRuleIdAndThreadsReplyId(Long ruleId, String threadsReplyId);

    /** 규칙 삭제 시 그 규칙의 자동응답 로그(FK 참조)도 함께 정리. */
    void deleteByRuleId(Long ruleId);
}
