package com.postflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReplyRepository extends JpaRepository<CommentReply, Long> {
    boolean existsByRuleIdAndThreadsReplyId(Long ruleId, String threadsReplyId);
}
