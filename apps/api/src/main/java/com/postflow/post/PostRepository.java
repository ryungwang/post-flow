package com.postflow.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** Due posts to publish: scheduled and the scheduled time has arrived. */
    List<Post> findByStatusAndScheduledAtLessThanEqual(PostStatus status, Instant now);

    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Threads 게시물 삭제 시 로컬 레코드 동기화용. */
    Optional<Post> findByUserIdAndThreadsMediaId(Long userId, String threadsMediaId);
}
