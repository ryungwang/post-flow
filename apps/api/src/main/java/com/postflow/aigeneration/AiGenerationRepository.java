package com.postflow.aigeneration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, Long> {
    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);
}
