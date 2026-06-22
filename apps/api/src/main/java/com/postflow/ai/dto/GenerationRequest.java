package com.postflow.ai.dto;

import com.postflow.ai.ModelTier;
import lombok.Builder;

/**
 * Vendor-neutral generation request. Volatile fields (prompt) are kept separate from
 * the stable systemPrompt so providers can apply prompt caching to the stable prefix.
 *
 * @param systemPrompt stable prefix (brand voice, style guide) — cache target
 * @param prompt       per-request user prompt (topic / goal / tone instructions)
 * @param outputSchema optional JSON schema for structured output (null = free text)
 * @param maxTokens    output cap
 * @param tier         model tier (LIGHT / STANDARD / PREMIUM)
 * @param cacheHint    whether the provider should attempt to cache the system prefix
 */
@Builder
public record GenerationRequest(
        String systemPrompt,
        String prompt,
        String outputSchema,
        int maxTokens,
        ModelTier tier,
        boolean cacheHint
) {
}
