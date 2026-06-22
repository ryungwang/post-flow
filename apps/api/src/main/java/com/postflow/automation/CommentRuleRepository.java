package com.postflow.automation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRuleRepository extends JpaRepository<CommentRule, Long> {
    List<CommentRule> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<CommentRule> findByActiveTrue();
}
