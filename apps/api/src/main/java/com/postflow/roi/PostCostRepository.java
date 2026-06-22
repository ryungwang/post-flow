package com.postflow.roi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostCostRepository extends JpaRepository<PostCost, Long> {
    List<PostCost> findByUserId(Long userId);
    Optional<PostCost> findByPostId(Long postId);
}
