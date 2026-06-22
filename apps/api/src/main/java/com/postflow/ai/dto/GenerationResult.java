package com.postflow.ai.dto;

import lombok.Builder;

/**
 * Vendor-neutral generation result. {@code provider} and {@code model} are persisted to
 * {@code ai_generations} for usage accounting and A/B comparison (see PRD → AI Engine).
 *
 * @param text         generated content (JSON when an outputSchema was supplied)
 * @param provider     provider id, e.g. "claude" / "openai"
 * @param model        concrete model id the provider resolved the tier to
 * @param inputTokens  prompt tokens billed
 * @param outputTokens completion tokens billed
 */
@Builder
public record GenerationResult(
        String text,
        String provider,
        String model,
        long inputTokens,
        long outputTokens
) {
}
