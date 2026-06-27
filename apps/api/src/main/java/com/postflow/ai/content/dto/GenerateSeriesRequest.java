package com.postflow.ai.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Series generation request (PRD → Content Series Generator: 7/14/30 days).
 *
 * @param topic 주제
 * @param days  생성 일수 (7 / 14 / 30)
 */
public record GenerateSeriesRequest(
        @NotBlank String topic,
        @Min(1) @Max(30) int days,
        String goal,
        Long brandId
) {
    public String goalOrDefault() {
        return goal == null || goal.isBlank() ? "Engagement" : goal;
    }
}
