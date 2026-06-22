package com.postflow.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** Due posts to publish: scheduled and the scheduled time has arrived. */
    List<Post> findByStatusAndScheduledAtLessThanEqual(PostStatus status, Instant now);

    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);
}
