package com.postflow.ai.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Content generation request (PRD → AI Content Generator).
 *
 * @param topic    주제 (자유 입력)
 * @param goal     목표 (Engagement / Followers / ... / Fun)
 * @param tone     톤 (Expert / Casual / Humor / ...)
 * @param quantity 생성 개수 (5 / 10 / 30)
 */
public record GenerateContentRequest(
        @NotBlank String topic,
        String goal,
        String tone,
        @Min(1) @Max(30) int quantity
) {
    public String goalOrDefault() {
        return goal == null || goal.isBlank() ? "Engagement" : goal;
    }

    public String toneOrDefault() {
        return tone == null || tone.isBlank() ? "Friendly" : tone;
    }
}
