package com.postflow.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalyticsRepository extends JpaRepository<PostAnalytics, Long> {
    List<PostAnalytics> findByPostIdIn(List<Long> postIds);
    Optional<PostAnalytics> findByPostId(Long postId);
}
