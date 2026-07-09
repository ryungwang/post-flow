package com.postflow.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostTargetRepository extends JpaRepository<PostTarget, Long> {

    List<PostTarget> findByPostIdOrderByIdAsc(Long postId);

    List<PostTarget> findByPostIdInOrderByIdAsc(List<Long> postIds);

    void deleteByPostId(Long postId);
}
