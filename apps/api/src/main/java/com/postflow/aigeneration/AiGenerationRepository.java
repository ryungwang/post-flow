package com.postflow.aigeneration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, Long> {
    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    /** 누적(총) 생성 수 — FREE 무료체험 한도용. */
    long countByUserId(Long userId);
}
